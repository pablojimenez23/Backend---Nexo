package com.nexo.msproducts.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductoRequestDTO(
        UUID tiendaId,
        UUID categoriaId,
        String nombre,
        String descripcion,
        BigDecimal precio,
        Integer stock,
        String imagenUrl
) {
}