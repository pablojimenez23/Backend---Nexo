package com.nexo.msproducts.messaging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Confirmación, reintentos y errores del consumidor base.
 * SQS está simulado: no se necesita AWS.
 */
class ConsumidorSqsTest {

    private static final String COLA = "cola-test";
    private static final String URL_COLA = "http://sqs/cola-test";
    private static final String URL_DLQ = "http://sqs/cola-test-dlq";
    private static final int ESPERA_MS = 3000;

    private SqsClient sqs;
    private ConsumidorSqs consumidor;

    @BeforeEach
    void preparar() {
        sqs = mock(SqsClient.class);
        when(sqs.getQueueUrl(any(GetQueueUrlRequest.class))).thenAnswer(inv -> {
            GetQueueUrlRequest req = inv.getArgument(0);
            return GetQueueUrlResponse.builder().queueUrl("http://sqs/" + req.queueName()).build();
        });
    }

    @AfterEach
    void detener() {
        if (consumidor != null) {
            consumidor.stop();
        }
    }

    // ---------- utilidades ----------

    private ConsumidorSqs crear(boolean habilitado, Consumer<String> logica) {
        return new ConsumidorSqs(sqs, COLA, habilitado) {
            @Override
            protected void procesar(String cuerpo) {
                logica.accept(cuerpo);
            }
        };
    }

    private Message mensaje(String cuerpo, Integer intentos) {
        Message.Builder b = Message.builder()
                .messageId("m-1")
                .receiptHandle("rh-1")
                .body(cuerpo);
        if (intentos != null) {
            b.attributes(Map.of(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, String.valueOf(intentos)));
        }
        return b.build();
    }

    /** SQS entrega el mensaje una sola vez; después responde vacío (null = nunca hay mensajes). */
    private void recibir(Message mensaje) {
        AtomicBoolean entregado = new AtomicBoolean(false);
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(inv -> {
            if (mensaje != null && entregado.compareAndSet(false, true)) {
                return ReceiveMessageResponse.builder().messages(mensaje).build();
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ReceiveMessageResponse.builder().build();
        });
    }

    private void iniciar(Consumer<String> logica) {
        consumidor = crear(true, logica);
        consumidor.start();
    }

    private ChangeMessageVisibilityRequest esperarBackoff() {
        ArgumentCaptor<ChangeMessageVisibilityRequest> captor =
                ArgumentCaptor.forClass(ChangeMessageVisibilityRequest.class);
        verify(sqs, timeout(ESPERA_MS)).changeMessageVisibility(captor.capture());
        return captor.getValue();
    }

    // ---------- éxito ----------

    @Test
    @DisplayName("Éxito: entrega el cuerpo a la lógica y borra el mensaje (ack)")
    void exito_borraElMensaje() {
        AtomicReference<String> recibido = new AtomicReference<>();
        recibir(mensaje("{\"evento\":\"cancelado\"}", 1));

        iniciar(recibido::set);

        ArgumentCaptor<DeleteMessageRequest> borrado = ArgumentCaptor.forClass(DeleteMessageRequest.class);
        verify(sqs, timeout(ESPERA_MS)).deleteMessage(borrado.capture());

        assertEquals("{\"evento\":\"cancelado\"}", recibido.get());
        assertEquals(URL_COLA, borrado.getValue().queueUrl());
        assertEquals("rh-1", borrado.getValue().receiptHandle());
        verify(sqs, never()).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
        verify(sqs, never()).sendMessage(any(SendMessageRequest.class));
    }

    // ---------- error recuperable ----------

    @Test
    @DisplayName("Error recuperable: no borra el mensaje y aplica backoff para que SQS lo reintente")
    void errorRecuperable_noBorra_yAplicaBackoff() {
        recibir(mensaje("{}", 1));

        iniciar(c -> {
            throw new RuntimeException("base de datos caída");
        });

        ChangeMessageVisibilityRequest req = esperarBackoff();
        assertEquals(URL_COLA, req.queueUrl());
        assertEquals("rh-1", req.receiptHandle());
        assertEquals(10, req.visibilityTimeout());
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
        verify(sqs, never()).sendMessage(any(SendMessageRequest.class));
    }

    @ParameterizedTest(name = "intento {0} -> espera {1} s")
    @CsvSource({"1,10", "2,20", "3,40", "4,80", "5,120", "8,120"})
    @DisplayName("El backoff crece al doble en cada intento y se limita a 120 s")
    void backoff_crece_yTieneTope(int intento, int esperado) {
        recibir(mensaje("{}", intento));

        iniciar(c -> {
            throw new RuntimeException("falla");
        });

        assertEquals(esperado, esperarBackoff().visibilityTimeout());
    }

    @Test
    @DisplayName("Sin contador de intentos en el mensaje se asume el primer intento")
    void sinContadorDeIntentos_usaElPrimero() {
        recibir(mensaje("{}", null));

        iniciar(c -> {
            throw new RuntimeException("falla");
        });

        assertEquals(10, esperarBackoff().visibilityTimeout());
    }

    @Test
    @DisplayName("Si falla el borrado tras procesar, se aplica backoff (la idempotencia evita duplicar el efecto)")
    void falloAlBorrar_aplicaBackoff() {
        recibir(mensaje("{}", 1));
        when(sqs.deleteMessage(any(DeleteMessageRequest.class))).thenThrow(new RuntimeException("red caída"));

        iniciar(c -> { });

        assertEquals(10, esperarBackoff().visibilityTimeout());
    }

    // ---------- error definitivo ----------

    @Test
    @DisplayName("Error definitivo: copia el mensaje a la DLQ con motivo y origen, y lo borra")
    void errorDefinitivo_copiaADlq_yBorra() {
        recibir(mensaje("basura", 1));

        iniciar(c -> {
            throw new MensajeInvalidoException("Falta el campo 'evento'");
        });

        ArgumentCaptor<DeleteMessageRequest> borrado = ArgumentCaptor.forClass(DeleteMessageRequest.class);
        verify(sqs, timeout(ESPERA_MS)).deleteMessage(borrado.capture());

        ArgumentCaptor<SendMessageRequest> enviado = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqs).sendMessage(enviado.capture());
        SendMessageRequest dlq = enviado.getValue();

        assertEquals(URL_DLQ, dlq.queueUrl());
        assertEquals("basura", dlq.messageBody());
        assertEquals("Falta el campo 'evento'", dlq.messageAttributes().get("motivo").stringValue());
        assertEquals(COLA, dlq.messageAttributes().get("origen").stringValue());
        assertEquals(URL_COLA, borrado.getValue().queueUrl());
        assertEquals("rh-1", borrado.getValue().receiptHandle());
        verify(sqs, never()).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
    }

    @Test
    @DisplayName("El motivo enviado a la DLQ se recorta a 500 caracteres")
    void errorDefinitivo_motivoLargo_seRecorta() {
        recibir(mensaje("x", 1));

        iniciar(c -> {
            throw new MensajeInvalidoException("m".repeat(800));
        });

        verify(sqs, timeout(ESPERA_MS)).deleteMessage(any(DeleteMessageRequest.class));
        ArgumentCaptor<SendMessageRequest> enviado = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqs).sendMessage(enviado.capture());
        assertEquals(500, enviado.getValue().messageAttributes().get("motivo").stringValue().length());
    }

    @Test
    @DisplayName("Si no se puede enviar a la DLQ, el mensaje NO se borra (no se pierde)")
    void errorDefinitivo_siFallaLaDlq_noBorra() {
        recibir(mensaje("basura", 1));
        when(sqs.sendMessage(any(SendMessageRequest.class))).thenThrow(new RuntimeException("sin permisos"));

        iniciar(c -> {
            throw new MensajeInvalidoException("inválido");
        });

        verify(sqs, timeout(ESPERA_MS)).sendMessage(any(SendMessageRequest.class));
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    // ---------- ciclo de vida ----------

    @Test
    @DisplayName("Deshabilitado: no lee la cola ni toca SQS")
    void deshabilitado_noLee() {
        consumidor = crear(false, c -> { });

        consumidor.start();

        assertFalse(consumidor.isRunning());
        verifyNoInteractions(sqs);
    }

    @Test
    @DisplayName("start() activa el consumidor y stop() lo detiene")
    void startYStop() {
        recibir(null);
        consumidor = crear(true, c -> { });

        consumidor.start();
        assertTrue(consumidor.isRunning());

        consumidor.stop();
        assertFalse(consumidor.isRunning());
    }
}