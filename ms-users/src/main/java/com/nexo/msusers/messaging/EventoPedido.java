package com.nexo.msusers.messaging;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Evento que publica ms-orders (copia del lado consumidor). */
public record EventoPedido(
        UUID eventoId,
        String evento,
        UUID pedidoId,
        UUID clienteId,
        UUID tiendaId,
        UUID conductorId,
        UUID ownerId,
        BigDecimal total,
        List<Item> items) {

    public record Item(UUID productoId, int cantidad) {
    }
}