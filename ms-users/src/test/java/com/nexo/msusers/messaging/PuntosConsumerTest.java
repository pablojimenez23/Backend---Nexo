package com.nexo.msusers.messaging;

import com.nexo.msusers.service.PuntosService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** El consumidor de puntos solo lee, valida y delega al servicio. */
class PuntosConsumerTest {

    private SqsClient sqs;
    private EventoPedidoLector lector;
    private PuntosService servicio;
    private PuntosConsumer consumidor;
    private EventoPedido evento;

    @BeforeEach
    void preparar() {
        sqs = mock(SqsClient.class);
        lector = mock(EventoPedidoLector.class);
        servicio = mock(PuntosService.class);
        MensajeriaProperties props = mock(MensajeriaProperties.class);
        when(props.colaPuntos()).thenReturn("nexo-puntos");
        when(props.consumidoresActivos()).thenReturn(false);

        consumidor = new PuntosConsumer(sqs, props, lector, servicio);
        evento = new EventoPedido(UUID.randomUUID(), "pedido.completado", UUID.randomUUID(),
                UUID.randomUUID(), null, null, null, BigDecimal.TEN, List.of());
    }

    @Test
    @DisplayName("Lee el mensaje y entrega el evento al servicio de puntos")
    void procesa_leeYDelega() {
        when(lector.leer("cuerpo")).thenReturn(evento);

        consumidor.procesar("cuerpo");

        InOrder orden = inOrder(lector, servicio);
        orden.verify(lector).leer("cuerpo");
        orden.verify(servicio).procesar(evento);
    }

    @Test
    @DisplayName("Mensaje inválido: se propaga (va a la DLQ) y el servicio no se ejecuta")
    void mensajeInvalido_sePropaga() {
        when(lector.leer("basura")).thenThrow(new MensajeInvalidoException("Falta el campo 'evento'"));

        assertThrows(MensajeInvalidoException.class, () -> consumidor.procesar("basura"));

        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("Falla del servicio: se propaga para que el mensaje se reintente")
    void fallaDelServicio_sePropaga() {
        when(lector.leer("cuerpo")).thenReturn(evento);
        doThrow(new IllegalStateException("base de datos caída")).when(servicio).procesar(evento);

        assertThrows(IllegalStateException.class, () -> consumidor.procesar("cuerpo"));
    }

    @Test
    @DisplayName("Con consumidores desactivados no lee la cola")
    void desactivado_noLee() {
        consumidor.start();

        assertFalse(consumidor.isRunning());
        verifyNoInteractions(sqs);
    }
}