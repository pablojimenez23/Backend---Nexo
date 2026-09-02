package com.nexo.msstores.dto;

import java.math.BigDecimal;

public record TiendaRequestDTO(
        String nombre,
        String descripcion,
        String direccion,
        BigDecimal latitud,
        BigDecimal longitud,
        String logoUrl,
        String horario,
        BigDecimal montoMinimo,
        String categoria
) {
}