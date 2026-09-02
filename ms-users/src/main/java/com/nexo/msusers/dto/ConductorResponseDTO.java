package com.nexo.msusers.dto;

import com.nexo.msusers.entity.Conductor;
import java.util.UUID;

public record ConductorResponseDTO(
        UUID id,
        UUID usuarioId,
        String nombre,
        String vehiculo,
        String patente,
        String estado
) {
    public static ConductorResponseDTO desde(Conductor c) {
        return new ConductorResponseDTO(
                c.getId(), c.getUsuario().getId(), c.getNombre(),
                c.getVehiculo(), c.getPatente(), c.getEstado().name()
        );
    }
}