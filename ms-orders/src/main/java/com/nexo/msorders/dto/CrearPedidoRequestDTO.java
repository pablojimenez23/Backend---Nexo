package com.nexo.msorders.dto;

import java.util.List;
import java.util.UUID;

/**
 * puntosACanjear es opcional: cantidad de puntos que el cliente paga en este pedido
 * (1 punto = $10), con el tope y el mínimo que define ms-orders. Va a nivel del pedido,
 * no por producto.
 *
 * confirmaMayorEdad es obligatorio (true) solo si el pedido incluye alcohol.
 *
 * En cada ítem, varianteId es obligatorio si el producto tiene variantes (tamaños),
 * opcionesIds son las opciones elegidas de los grupos del producto (masa, extras...) y
 * nota es una aclaración opcional para la tienda ("sin cebolla", hasta 140 caracteres).
 */
public record CrearPedidoRequestDTO(
        UUID tiendaId,
        String direccionEnvio,
        List<ItemRequest> items,
        Long puntosACanjear,
        Boolean confirmaMayorEdad
) {
    /** Sin puntos: el pedido se paga completo con dinero. */
    public CrearPedidoRequestDTO(UUID tiendaId, String direccionEnvio, List<ItemRequest> items) {
        this(tiendaId, direccionEnvio, items, 0L, false);
    }

    public CrearPedidoRequestDTO(UUID tiendaId, String direccionEnvio, List<ItemRequest> items, Long puntosACanjear) {
        this(tiendaId, direccionEnvio, items, puntosACanjear, false);
    }

    public record ItemRequest(UUID productoId, Integer cantidad, UUID varianteId,
                              List<UUID> opcionesIds, String nota) {
        public ItemRequest(UUID productoId, Integer cantidad) {
            this(productoId, cantidad, null, List.of(), null);
        }

        public ItemRequest(UUID productoId, Integer cantidad, UUID varianteId, List<UUID> opcionesIds) {
            this(productoId, cantidad, varianteId, opcionesIds, null);
        }
    }
}