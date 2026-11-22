package com.nexo.msproducts.service;

import com.nexo.msproducts.messaging.EventoPedido;
import com.nexo.msproducts.repository.EventoProcesadoRepository;
import com.nexo.msproducts.repository.ProductoRepository;
import com.nexo.msproducts.repository.VarianteProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock private ProductoRepository productos;
    @Mock private VarianteProductoRepository variantes;
    @Mock private EventoProcesadoRepository procesados;

    private StockService service;

    @BeforeEach
    void setUp() {
        service = new StockService(productos, variantes, procesados);
    }

    private EventoPedido evento(String nombre, UUID eventoId, List<EventoPedido.Item> items) {
        return new EventoPedido(eventoId, nombre, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, null, new BigDecimal("10000"), items);
    }

    @Test
    void procesar_eventoQueNoEsCancelacion_noHaceNada() {
        service.procesar(evento("pedido.pagado", UUID.randomUUID(),
                List.of(new EventoPedido.Item(UUID.randomUUID(), 1))));

        verifyNoInteractions(procesados, productos, variantes);
    }

    @Test
    void procesar_eventoYaProcesado_noDevuelveStockDeNuevo() {
        UUID eventoId = UUID.randomUUID();
        when(procesados.registrar("stock:" + eventoId)).thenReturn(0);

        service.procesar(evento("pedido.cancelado", eventoId,
                List.of(new EventoPedido.Item(UUID.randomUUID(), 2))));

        verifyNoInteractions(productos, variantes);
    }

    @Test
    void procesar_cancelacionSinItems_noDevuelveNada() {
        UUID eventoId = UUID.randomUUID();
        when(procesados.registrar("stock:" + eventoId)).thenReturn(1);

        service.procesar(evento("pedido.cancelado", eventoId, List.of()));

        verifyNoInteractions(productos, variantes);
    }

    @Test
    void procesar_productoSinTamano_devuelveElStockAlProducto() {
        UUID eventoId = UUID.randomUUID();
        UUID productoId = UUID.randomUUID();
        when(procesados.registrar("stock:" + eventoId)).thenReturn(1);

        service.procesar(evento("pedido.cancelado", eventoId,
                List.of(new EventoPedido.Item(productoId, 3))));

        verify(productos).revertirStock(productoId, 3);
        verifyNoInteractions(variantes);
    }

    @Test
    void procesar_productoConTamano_devuelveElStockALaVarianteYAlProducto() {
        UUID eventoId = UUID.randomUUID();
        UUID productoId = UUID.randomUUID();
        UUID varianteId = UUID.randomUUID();
        when(procesados.registrar("stock:" + eventoId)).thenReturn(1);
        when(variantes.revertirStock(varianteId, productoId, 2)).thenReturn(1);

        service.procesar(evento("pedido.cancelado", eventoId,
                List.of(new EventoPedido.Item(productoId, 2, varianteId))));

        verify(variantes).revertirStock(varianteId, productoId, 2);
        verify(productos).revertirStock(productoId, 2);
    }

    @Test
    void procesar_varianteQueYaNoExiste_noTocaElStockDelProducto() {
        UUID eventoId = UUID.randomUUID();
        UUID productoId = UUID.randomUUID();
        UUID varianteId = UUID.randomUUID();
        when(procesados.registrar("stock:" + eventoId)).thenReturn(1);
        when(variantes.revertirStock(varianteId, productoId, 2)).thenReturn(0);

        service.procesar(evento("pedido.cancelado", eventoId,
                List.of(new EventoPedido.Item(productoId, 2, varianteId))));

        verify(variantes).revertirStock(varianteId, productoId, 2);
        verify(productos, never()).revertirStock(any(), anyInt());
    }

    @Test
    void procesar_variosItems_devuelveCadaUnoPorSuCamino() {
        UUID eventoId = UUID.randomUUID();
        UUID sinTamano = UUID.randomUUID();
        UUID conTamano = UUID.randomUUID();
        UUID varianteId = UUID.randomUUID();
        when(procesados.registrar("stock:" + eventoId)).thenReturn(1);
        when(variantes.revertirStock(varianteId, conTamano, 1)).thenReturn(1);

        service.procesar(evento("pedido.cancelado", eventoId, List.of(
                new EventoPedido.Item(sinTamano, 4),
                new EventoPedido.Item(conTamano, 1, varianteId))));

        verify(productos).revertirStock(sinTamano, 4);
        verify(variantes).revertirStock(varianteId, conTamano, 1);
        verify(productos).revertirStock(conTamano, 1);
    }
}