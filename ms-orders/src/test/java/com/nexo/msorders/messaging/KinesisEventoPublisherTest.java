package com.nexo.msorders.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KinesisEventoPublisherTest {

    @Mock private KinesisClient kinesis;
    private KinesisEventoPublisher publisher;

    private final UUID pedidoId = UUID.randomUUID();
    private final UUID clienteId = UUID.randomUUID();
    private final UUID conductorId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID tiendaId = UUID.randomUUID();
    private final UUID productoId = UUID.randomUUID();

    @BeforeEach
    void preparar() {
        publisher = new KinesisEventoPublisher(kinesis, "nexo-pedidos-stream");
    }

    private EventoPedido evento() {
        return new EventoPedido(UUID.randomUUID(), "pedido.pagado", pedidoId, clienteId, tiendaId,
                conductorId, ownerId, new BigDecimal("12000"),
                List.of(new EventoPedido.Item(productoId, 2)), Instant.parse("2026-11-16T12:00:00Z"));
    }

    @Test
    void enviaElRegistroAlStreamConElPedidoComoClaveDeParticion() {
        publisher.publicar(evento());

        ArgumentCaptor<PutRecordRequest> captor = ArgumentCaptor.forClass(PutRecordRequest.class);
        verify(kinesis).putRecord(captor.capture());
        assertEquals("nexo-pedidos-stream", captor.getValue().streamName());
        assertEquals(pedidoId.toString(), captor.getValue().partitionKey());
        String json = captor.getValue().data().asUtf8String();
        assertTrue(json.contains("\"evento\":\"pedido.pagado\""));
        assertTrue(json.contains("\"pedidoId\":\"" + pedidoId + "\""));
        assertTrue(json.contains("\"cantidad\":2"));
    }

    @Test
    void elRegistroNoIncluyeDatosPersonales() {
        String json = KinesisEventoPublisher.construirJson(evento());

        assertFalse(json.contains(clienteId.toString()));
        assertFalse(json.contains(conductorId.toString()));
        assertFalse(json.contains(ownerId.toString()));
        assertFalse(json.contains("clienteId"));
        assertTrue(json.contains(tiendaId.toString()));
    }

    @Test
    void unFalloDeKinesisNoHaceFallarLaPublicacion() {
        when(kinesis.putRecord(any(PutRecordRequest.class))).thenThrow(new RuntimeException("sin permisos"));

        assertDoesNotThrow(() -> publisher.publicar(evento()));
        verify(kinesis).putRecord(any(PutRecordRequest.class));
    }

    @Test
    void sinPedidoUsaElEventoComoClaveDeParticion() {
        EventoPedido sinPedido = new EventoPedido(UUID.randomUUID(), "pedido.listo", null, null, tiendaId,
                null, null, null, List.of(), Instant.now());

        publisher.publicar(sinPedido);

        ArgumentCaptor<PutRecordRequest> captor = ArgumentCaptor.forClass(PutRecordRequest.class);
        verify(kinesis).putRecord(captor.capture());
        assertEquals(sinPedido.eventoId().toString(), captor.getValue().partitionKey());
    }
}