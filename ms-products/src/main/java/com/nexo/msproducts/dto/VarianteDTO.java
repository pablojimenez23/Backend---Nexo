package com.nexo.msproducts.dto;

import com.nexo.msproducts.entity.Oferta;
import com.nexo.msproducts.entity.VarianteProducto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Se usa para enviar y para recibir. precioOferta solo se llena al responder
 * (cuando el producto tiene una oferta vigente); al recibir se ignora.
 */
public record VarianteDTO(
        UUID id,
        String nombre,
        BigDecimal precio,
        Integer stock,
        Boolean disponible,
        BigDecimal precioOferta
) {
    public VarianteDTO(UUID id, String nombre, BigDecimal precio, Integer stock, Boolean disponible) {
        this(id, nombre, precio, stock, disponible, null);
    }

    public static VarianteDTO desde(VarianteProducto v, Oferta oferta) {
        return new VarianteDTO(
                v.getId(), v.getNombre(), v.getPrecio(), v.getStock(), v.isDisponible(),
                oferta == null ? null : oferta.precioSobre(v.getPrecio())
        );
    }
}