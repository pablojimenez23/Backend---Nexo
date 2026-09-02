package com.nexo.msorders.dto;

import java.util.List;
import java.util.UUID;

public record CrearPedidoRequestDTO(
        UUID tiendaId,
        String direccionEnvio,
        List<ItemRequest> items
) {
    public record ItemRequest(UUID productoId, Integer cantidad) {
    }
}