package com.nexo.msorders.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;

import java.util.List;

/**
 * Publica cada evento de pedido en un stream de Amazon Kinesis para tenerlo como flujo en tiempo real.
 * El registro lleva solo identificadores y montos: no incluye datos personales (ni clienteId,
 * conductorId ni ownerId). La clave de partición es el pedido, así que los eventos de un mismo
 * pedido llegan en orden. Igual que en SNS, se envía después de confirmar la transacción y un
 * fallo de Kinesis nunca hace fallar el pedido.
 */
@Component
public class KinesisEventoPublisher implements EventoPublisher {

    private static final Logger log = LoggerFactory.getLogger(KinesisEventoPublisher.class);

    private final KinesisClient kinesis;
    private final String stream;

    public KinesisEventoPublisher(KinesisClient kinesis,
                                  @Value("${nexo.mensajeria.stream-pedidos:nexo-pedidos-stream}") String stream) {
        this.kinesis = kinesis;
        this.stream = stream;
    }

    @Override
    public void publicar(EventoPedido evento) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviar(evento);
                }
            });
        } else {
            enviar(evento);
        }
    }

    void enviar(EventoPedido evento) {
        try {
            String clave = evento.pedidoId() != null
                    ? evento.pedidoId().toString()
                    : String.valueOf(evento.eventoId());
            kinesis.putRecord(PutRecordRequest.builder()
                    .streamName(stream)
                    .partitionKey(clave)
                    .data(SdkBytes.fromUtf8String(construirJson(evento)))
                    .build());
            log.info("Evento {} enviado a Kinesis (pedido {})", evento.evento(), evento.pedidoId());
        } catch (Exception e) {
            log.error("No se pudo enviar {} del pedido {} a Kinesis: {}",
                    evento.evento(), evento.pedidoId(), e.getMessage());
        }
    }

    /** JSON sin datos personales: solo identificadores del pedido, la tienda y los productos. */
    static String construirJson(EventoPedido e) {
        StringBuilder sb = new StringBuilder(200);
        sb.append('{')
          .append("\"eventoId\":").append(q(e.eventoId())).append(',')
          .append("\"evento\":\"").append(e.evento()).append("\",")
          .append("\"pedidoId\":").append(q(e.pedidoId())).append(',')
          .append("\"tiendaId\":").append(q(e.tiendaId())).append(',')
          .append("\"total\":").append(e.total() == null ? "null" : e.total().toPlainString()).append(',')
          .append("\"ocurridoEn\":\"").append(e.ocurridoEn()).append("\",")
          .append("\"items\":[");
        List<EventoPedido.Item> items = e.items();
        for (int i = 0; i < items.size(); i++) {
            EventoPedido.Item it = items.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"productoId\":").append(q(it.productoId()))
              .append(",\"cantidad\":").append(it.cantidad()).append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String q(Object id) {
        return id == null ? "null" : "\"" + id + "\"";
    }
}