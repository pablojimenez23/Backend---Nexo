package com.nexo.msproducts.dto;

import com.nexo.msproducts.entity.Oferta;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OfertaResponseDTO(
        UUID id,
        UUID productoId,
        UUID tiendaId,
        int porcentaje,
        BigDecimal precioOferta,
        Instant inicio,
        Instant fin,
        Integer cupo,
        int unidadesVendidas,
        Integer unidadesDisponibles,
        int limitePorPedido,
        String estado
) {
    public static OfertaResponseDTO desde(Oferta o, BigDecimal precioProducto) {
        Integer disponibles = o.getCupo() == null
                ? null
                : Math.max(0, o.getCupo() - o.getUnidadesVendidas());
        BigDecimal precioOferta = precioProducto == null ? null : o.precioSobre(precioProducto);
        return new OfertaResponseDTO(
                o.getId(), o.getProductoId(), o.getTiendaId(), o.getPorcentaje(), precioOferta,
                o.getInicio(), o.getFin(), o.getCupo(), o.getUnidadesVendidas(), disponibles,
                o.getLimitePorPedido(), o.getEstado().name()
        );
    }
}