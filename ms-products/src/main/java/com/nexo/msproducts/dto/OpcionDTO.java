package com.nexo.msproducts.dto;

import com.nexo.msproducts.entity.OpcionProducto;

import java.math.BigDecimal;
import java.util.UUID;

public record OpcionDTO(
        UUID id,
        String nombre,
        BigDecimal precioAdicional,
        Boolean disponible
) {
    public static OpcionDTO desde(OpcionProducto o) {
        return new OpcionDTO(o.getId(), o.getNombre(), o.getPrecioAdicional(), o.isDisponible());
    }
}