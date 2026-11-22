package com.nexo.msproducts.service;

import com.nexo.msproducts.messaging.EventoPedido;
import com.nexo.msproducts.repository.EventoProcesadoRepository;
import com.nexo.msproducts.repository.ProductoRepository;
import com.nexo.msproducts.repository.VarianteProductoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    private final ProductoRepository productos;
    private final VarianteProductoRepository variantes;
    private final EventoProcesadoRepository procesados;

    public StockService(ProductoRepository productos,
                        VarianteProductoRepository variantes,
                        EventoProcesadoRepository procesados) {
        this.productos = productos;
        this.variantes = variantes;
        this.procesados = procesados;
    }

    /**
     * Devuelve el stock de cada item de un pedido cancelado.
     * Si el item tiene tamaño se devuelve a la variante (y al total del producto);
     * si no, solo al producto.
     * El registro del evento y las actualizaciones van en una sola transacción:
     * si algo falla se deshace todo y el reintento no duplica la devolución.
     */
    @Transactional
    public void procesar(EventoPedido e) {
        if (!"pedido.cancelado".equals(e.evento())) {
            return;
        }
        if (procesados.registrar("stock:" + e.eventoId()) == 0) {
            log.info("Evento {} ya procesado, se ignora", e.eventoId());
            return;
        }
        if (e.items().isEmpty()) {
            log.warn("El pedido {} se canceló sin items, no hay stock que devolver", e.pedidoId());
            return;
        }
        e.items().forEach(item -> {
            if (item.varianteId() == null) {
                productos.revertirStock(item.productoId(), item.cantidad());
            } else if (variantes.revertirStock(item.varianteId(), item.productoId(), item.cantidad()) > 0) {
                productos.revertirStock(item.productoId(), item.cantidad());
            } else {
                log.warn("La variante {} del producto {} ya no existe, no se devuelve su stock",
                        item.varianteId(), item.productoId());
            }
        });
        log.info("Stock devuelto de {} producto(s) por la cancelación del pedido {}",
                e.items().size(), e.pedidoId());
    }
}