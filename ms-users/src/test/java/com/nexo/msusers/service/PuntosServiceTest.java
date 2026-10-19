package com.nexo.msusers.service;

import com.nexo.msusers.entity.MovimientoPuntos;
import com.nexo.msusers.messaging.EventoPedido;
import com.nexo.msusers.messaging.MensajeInvalidoException;
import com.nexo.msusers.repository.EventoProcesadoRepository;
import com.nexo.msusers.repository.MovimientoPuntosRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Regla de puntos e idempotencia del consumidor de puntos. Sin base de datos. */
class PuntosServiceTest {

    private static final UUID EVENTO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PEDIDO_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CLIENTE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private MovimientoPuntosRepository movimientos;
    private EventoProcesadoRepository procesados;
    private PuntosService servicio;

    @BeforeEach
    void preparar() {
        movimientos = mock(MovimientoPuntosRepository.class);
        procesados = mock(EventoProcesadoRepository.class);
        servicio = new PuntosService(movimientos, procesados);
        ReflectionTestUtils.setField(servicio, "pesosPorPunto", 100L);
        when(procesados.registrar(anyString())).thenReturn(1);
    }

    private EventoPedido evento(String nombre, UUID cliente, BigDecimal total) {
        return new EventoPedido(EVENTO_ID, nombre, PEDIDO_ID, cliente, null, null, null, total, List.of());
    }

    @ParameterizedTest(name = "total {0} con {1} pesos por punto -> {2} puntos")
    @CsvSource({"15990,100,159", "100,100,1", "15990.99,100,159", "15990,50,319"})
    @DisplayName("Pedido completado: acredita total / pesos por punto, sin decimales")
    void acreditaPuntos(BigDecimal total, long pesosPorPunto, long esperados) {
        ReflectionTestUtils.setField(servicio, "pesosPorPunto", pesosPorPunto);

        servicio.procesar(evento("pedido.completado", CLIENTE_ID, total));

        ArgumentCaptor<MovimientoPuntos> captor = ArgumentCaptor.forClass(MovimientoPuntos.class);
        verify(movimientos).save(captor.capture());
        assertEquals(CLIENTE_ID, captor.getValue().getUsuarioId());
        assertEquals(PEDIDO_ID, captor.getValue().getPedidoId());
        assertEquals(esperados, captor.getValue().getPuntos());
    }

    @Test
    @DisplayName("Total menor al valor de un punto: no se guarda ningún movimiento")
    void totalBajo_noAcredita() {
        servicio.procesar(evento("pedido.completado", CLIENTE_ID, new BigDecimal("99")));

        verify(movimientos, never()).save(any(MovimientoPuntos.class));
    }

    @Test
    @DisplayName("Registra el evento como procesado con la clave puntos:eventoId")
    void registraElEvento() {
        servicio.procesar(evento("pedido.completado", CLIENTE_ID, new BigDecimal("15990")));

        verify(procesados).registrar("puntos:" + EVENTO_ID);
    }

    @Test
    @DisplayName("Evento duplicado: se ignora y no se acreditan puntos dos veces")
    void eventoDuplicado_seIgnora() {
        when(procesados.registrar(anyString())).thenReturn(0);

        servicio.procesar(evento("pedido.completado", CLIENTE_ID, new BigDecimal("15990")));

        verify(movimientos, never()).existsByPedidoId(any(UUID.class));
        verify(movimientos, never()).save(any(MovimientoPuntos.class));
    }

    @Test
    @DisplayName("Si el pedido ya tiene puntos acreditados, no se acreditan de nuevo")
    void pedidoYaAcreditado_seIgnora() {
        when(movimientos.existsByPedidoId(PEDIDO_ID)).thenReturn(true);

        servicio.procesar(evento("pedido.completado", CLIENTE_ID, new BigDecimal("15990")));

        verify(movimientos, never()).save(any(MovimientoPuntos.class));
    }

    @Test
    @DisplayName("Otros eventos (no completado) no hacen nada")
    void otroEvento_noHaceNada() {
        servicio.procesar(evento("pedido.cancelado", CLIENTE_ID, new BigDecimal("15990")));

        verifyNoInteractions(procesados, movimientos);
    }

    @Test
    @DisplayName("Completado sin clienteId: mensaje inválido (va a la DLQ)")
    void sinCliente_esInvalido() {
        assertThrows(MensajeInvalidoException.class,
                () -> servicio.procesar(evento("pedido.completado", null, new BigDecimal("15990"))));

        verifyNoInteractions(procesados, movimientos);
    }

    @Test
    @DisplayName("Completado sin total: mensaje inválido (va a la DLQ)")
    void sinTotal_esInvalido() {
        assertThrows(MensajeInvalidoException.class,
                () -> servicio.procesar(evento("pedido.completado", CLIENTE_ID, null)));

        verifyNoInteractions(procesados, movimientos);
    }
}