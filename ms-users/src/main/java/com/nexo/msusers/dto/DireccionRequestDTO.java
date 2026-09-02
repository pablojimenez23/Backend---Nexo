package com.nexo.msusers.dto;

import java.math.BigDecimal;

public record DireccionRequestDTO(
        String calle,
        String ciudad,
        String region,
        String codigoPostal,
        String tipo,
        BigDecimal latitud,
        BigDecimal longitud
) {
}