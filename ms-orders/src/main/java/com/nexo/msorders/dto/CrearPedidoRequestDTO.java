package com.nexo.msorders.dto;

import java.util.List;
import java.util.UUID;

public record CrearPedidoRequestDTO(
        UUID tiendaId,
        String direccionEnvio,
        List<ItemRequest> items
) {
    /** canjearConPuntos es opcional: si viene en true, esa línea se paga con puntos (1 punto = $10). */
    public record ItemRequest(UUID productoId, Integer cantidad, Boolean canjearConPuntos) {

        public ItemRequest(UUID productoId, Integer cantidad) {
            this(productoId, cantidad, false);
        }
    }
}