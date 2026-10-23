package com.nexo.msproducts.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Respuesta de reservar-stock: cuántas de las unidades reservadas se cobran a precio de oferta.
 * Sin oferta aplicada: ofertaId y precioOferta son null y unidadesEnOferta es 0.
 */
public record ReservaStockResponseDTO(
        UUID ofertaId,
        BigDecimal precioOferta,
        int unidadesEnOferta
) {
}