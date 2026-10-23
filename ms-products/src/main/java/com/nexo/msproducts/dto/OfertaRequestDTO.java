package com.nexo.msproducts.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * duracionHoras es obligatoria. inicio es opcional (null = ahora, formato ISO como 2026-10-24T15:00:00Z).
 * cupo es opcional (null = sin tope de unidades). limitePorPedido es opcional (null = valor por defecto).
 */
public record OfertaRequestDTO(
        UUID productoId,
        Integer porcentaje,
        Integer duracionHoras,
        Instant inicio,
        Integer cupo,
        Integer limitePorPedido
) {
}