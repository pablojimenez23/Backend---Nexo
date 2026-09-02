package com.nexo.msusers.dto;

import com.nexo.msusers.entity.Direccion;
import java.math.BigDecimal;
import java.util.UUID;

public record DireccionResponseDTO(
        UUID id,
        String calle,
        String ciudad,
        String region,
        String codigoPostal,
        String tipo,
        BigDecimal latitud,
        BigDecimal longitud
) {
    public static DireccionResponseDTO desde(Direccion d) {
        return new DireccionResponseDTO(
                d.getId(), d.getCalle(), d.getCiudad(), d.getRegion(),
                d.getCodigoPostal(), d.getTipo().name(), d.getLatitud(), d.getLongitud()
        );
    }
}