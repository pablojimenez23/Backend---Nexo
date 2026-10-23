package com.nexo.msorders.dto;

import java.util.List;
import java.util.UUID;

/**
 * puntosACanjear es opcional: cantidad de puntos que el cliente paga en este pedido
 * (1 punto = $10), con el tope y el mínimo que define ms-orders. Va a nivel del pedido,
 * no por producto.
 */
public record CrearPedidoRequestDTO(
        UUID tiendaId,
        String direccionEnvio,
        List<ItemRequest> items,
        Long puntosACanjear
) {
    /** Sin puntos: el pedido se paga completo con dinero. */
    public CrearPedidoRequestDTO(UUID tiendaId, String direccionEnvio, List<ItemRequest> items) {
        this(tiendaId, direccionEnvio, items, 0L);
    }

    public record ItemRequest(UUID productoId, Integer cantidad) {
    }
}