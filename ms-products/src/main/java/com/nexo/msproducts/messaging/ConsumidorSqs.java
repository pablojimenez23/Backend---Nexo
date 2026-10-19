package com.nexo.msproducts.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Map;

/**
 * Consumidor base: lee una cola SQS con long polling en un hilo propio.
 * Las subclases solo implementan procesar().
 *  - Éxito: se borra el mensaje (ack).
 *  - Error recuperable: no se borra; reaparece con backoff y, al agotar los intentos
 *    de la cola, SQS lo mueve a la DLQ.
 *  - Error definitivo (MensajeInvalidoException): se copia a la DLQ y se borra.
 */
public abstract class ConsumidorSqs implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorSqs.class);

    private static final int ESPERA_LARGA_SEG = 20;
    private static final int MAX_MENSAJES = 5;
    private static final int BACKOFF_BASE_SEG = 10;
    private static final int BACKOFF_MAX_SEG = 120;

    private final SqsClient sqs;
    private final String nombreCola;
    private final boolean habilitado;

    private volatile boolean activo;
    private Thread hilo;
    private String urlCola;

    protected ConsumidorSqs(SqsClient sqs, String nombreCola, boolean habilitado) {
        this.sqs = sqs;
        this.nombreCola = nombreCola;
        this.habilitado = habilitado;
    }

    /** Lógica de negocio. Lanzar MensajeInvalidoException si el mensaje nunca podrá procesarse. */
    protected abstract void procesar(String cuerpo);

    // ---------- ciclo de vida ----------

    @Override
    public synchronized void start() {
        if (!habilitado || activo) {
            return;
        }
        activo = true;
        hilo = new Thread(this::bucle, "sqs-" + nombreCola);
        hilo.setDaemon(true);
        hilo.start();
        log.info("Consumidor iniciado para la cola {}", nombreCola);
    }

    @Override
    public synchronized void stop() {
        activo = false;
        if (hilo != null) {
            hilo.interrupt();
            try {
                hilo.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            hilo = null;
        }
    }

    @Override
    public boolean isRunning() {
        return activo;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    // ---------- bucle de lectura ----------

    private void bucle() {
        while (activo) {
            try {
                if (urlCola == null && !resolverCola()) {
                    dormir(10_000);
                    continue;
                }
                var respuesta = sqs.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(urlCola)
                        .maxNumberOfMessages(MAX_MENSAJES)
                        .waitTimeSeconds(ESPERA_LARGA_SEG)
                        .messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)
                        .build());
                for (Message mensaje : respuesta.messages()) {
                    manejar(mensaje);
                }
            } catch (Exception e) {
                if (activo) {
                    log.warn("Error leyendo la cola {}: {}", nombreCola, e.getMessage());
                    dormir(5_000);
                }
            }
        }
    }

    private boolean resolverCola() {
        try {
            urlCola = sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(nombreCola).build()).queueUrl();
            return true;
        } catch (QueueDoesNotExistException e) {
            log.warn("La cola {} todavía no existe (¿ms-queues ya arrancó?). Reintento en 10 s", nombreCola);
            return false;
        }
    }

    private void manejar(Message mensaje) {
        try {
            procesar(mensaje.body());
            eliminar(mensaje);
        } catch (MensajeInvalidoException e) {
            enviarADlq(mensaje, e.getMessage());
        } catch (Exception e) {
            reintentarMasTarde(mensaje, e);
        }
    }

    // ---------- ack / reintento / DLQ ----------

    private void eliminar(Message mensaje) {
        sqs.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(urlCola)
                .receiptHandle(mensaje.receiptHandle())
                .build());
    }

    private void reintentarMasTarde(Message mensaje, Exception causa) {
        int intentos = intentos(mensaje);
        int espera = (int) Math.min((long) BACKOFF_BASE_SEG << Math.min(intentos - 1, 10), BACKOFF_MAX_SEG);
        log.warn("Falló el mensaje {} (intento {}): {}. Se reintenta en {} s",
                mensaje.messageId(), intentos, causa.getMessage(), espera);
        try {
            sqs.changeMessageVisibility(ChangeMessageVisibilityRequest.builder()
                    .queueUrl(urlCola)
                    .receiptHandle(mensaje.receiptHandle())
                    .visibilityTimeout(espera)
                    .build());
        } catch (Exception e) {
            log.warn("No se pudo aplicar el backoff al mensaje {}: {}", mensaje.messageId(), e.getMessage());
        }
    }

    private void enviarADlq(Message mensaje, String motivo) {
        try {
            String urlDlq = sqs.getQueueUrl(GetQueueUrlRequest.builder()
                    .queueName(nombreCola + "-dlq").build()).queueUrl();
            sqs.sendMessage(SendMessageRequest.builder()
                    .queueUrl(urlDlq)
                    .messageBody(mensaje.body())
                    .messageAttributes(Map.of(
                            "motivo", atributo(motivo),
                            "origen", atributo(nombreCola)))
                    .build());
            eliminar(mensaje);
            log.error("Mensaje {} descartado a la DLQ: {}", mensaje.messageId(), motivo);
        } catch (Exception e) {
            log.error("No se pudo enviar el mensaje {} a la DLQ: {}", mensaje.messageId(), e.getMessage());
        }
    }

    private MessageAttributeValue atributo(String valor) {
        String recortado = valor == null ? "" : (valor.length() > 500 ? valor.substring(0, 500) : valor);
        return MessageAttributeValue.builder().dataType("String").stringValue(recortado).build();
    }

    private int intentos(Message mensaje) {
        try {
            return Integer.parseInt(mensaje.attributes().get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT));
        } catch (RuntimeException e) {
            return 1;
        }
    }

    private void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}