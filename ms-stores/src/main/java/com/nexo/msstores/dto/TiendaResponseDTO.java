package com.nexo.msstores.dto;

import com.nexo.msstores.entity.Tienda;
import java.math.BigDecimal;
import java.util.UUID;

public record TiendaResponseDTO(
        UUID id,
        UUID ownerId,
        String nombre,
        String descripcion,
        String direccion,
        BigDecimal latitud,
        BigDecimal longitud,
        String logoUrl,
        String horario,
        BigDecimal montoMinimo,
        String estado,
        String motivoRechazo
) {
    public static TiendaResponseDTO desde(Tienda t) {
        return new TiendaResponseDTO(
                t.getId(), t.getOwnerId(), t.getNombre(), t.getDescripcion(),
                t.getDireccion(), t.getLatitud(), t.getLongitud(), t.getLogoUrl(),
                t.getHorario(), t.getMontoMinimo(), t.getEstado().name(), t.getMotivoRechazo()
        );
    }
}