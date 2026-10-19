package com.nexo.msproducts.service;

import com.nexo.msproducts.messaging.EventoPedido;
import com.nexo.msproducts.repository.EventoProcesadoRepository;
import com.nexo.msproducts.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Devolución de stock al cancelar un pedido, con idempotencia. Sin base de datos. */
class StockServiceTest {

    private static final UUID EVENTO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PEDIDO_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PRODUCTO_A = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID PRODUCTO_B = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private ProductoRepository productos;
    private EventoProcesadoRepository procesados;
    private StockService servicio;

    @BeforeEach
    void preparar() {
        productos = mock(ProductoRepository.class);
        procesados = mock(EventoProcesadoRepository.class);
        servicio = new StockService(productos, procesados);
        when(procesados.registrar(anyString())).thenReturn(1);
    }

    private EventoPedido evento(String nombre, List<EventoPedido.Item> items) {
        return new EventoPedido(EVENTO_ID, nombre, PEDIDO_ID, null, null, null, null, BigDecimal.TEN, items);
    }

    @Test
    @DisplayName("Pedido cancelado: devuelve el stock de cada item")
    void cancelado_devuelveStockDeCadaItem() {
        servicio.procesar(evento("pedido.cancelado", List.of(
                new EventoPedido.Item(PRODUCTO_A, 2),
                new EventoPedido.Item(PRODUCTO_B, 5))));

        verify(productos).revertirStock(PRODUCTO_A, 2);
        verify(productos).revertirStock(PRODUCTO_B, 5);
    }

    @Test
    @DisplayName("Registra el evento como procesado con la clave stock:eventoId")
    void registraElEvento() {
        servicio.procesar(evento("pedido.cancelado", List.of(new EventoPedido.Item(PRODUCTO_A, 1))));

        verify(procesados).registrar("stock:" + EVENTO_ID);
    }

    @Test
    @DisplayName("Evento duplicado: se ignora y no se devuelve el stock dos veces")
    void eventoDuplicado_seIgnora() {
        when(procesados.registrar(anyString())).thenReturn(0);

        servicio.procesar(evento("pedido.cancelado", List.of(new EventoPedido.Item(PRODUCTO_A, 2))));

        verify(productos, never()).revertirStock(any(UUID.class), anyInt());
    }

    @Test
    @DisplayName("Otros eventos (no cancelado) no hacen nada")
    void otroEvento_noHaceNada() {
        servicio.procesar(evento("pedido.pagado", List.of(new EventoPedido.Item(PRODUCTO_A, 2))));

        verifyNoInteractions(productos, procesados);
    }

    @Test
    @DisplayName("Cancelado sin items: no hay stock que devolver")
    void sinItems_noDevuelveNada() {
        servicio.procesar(evento("pedido.cancelado", List.of()));

        verify(productos, never()).revertirStock(any(UUID.class), anyInt());
    }

    @Test
    @DisplayName("Si falla la actualización, el error se propaga (se deshace la transacción y se reintenta)")
    void falla_sePropaga() {
        doThrow(new IllegalStateException("base de datos caída"))
                .when(productos).revertirStock(any(UUID.class), anyInt());

        assertThrows(IllegalStateException.class, () -> servicio.procesar(
                evento("pedido.cancelado", List.of(new EventoPedido.Item(PRODUCTO_A, 2)))));
    }
}