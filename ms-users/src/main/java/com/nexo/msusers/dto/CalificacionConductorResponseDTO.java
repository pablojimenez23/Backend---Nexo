package com.nexo.msusers.dto;

import com.nexo.msusers.entity.CalificacionConductor;
import java.time.Instant;
import java.util.UUID;

public record CalificacionConductorResponseDTO(
        UUID id,
        UUID conductorId,
        Integer puntaje,
        String comentario,
        Instant creadoEn
) {
    public static CalificacionConductorResponseDTO desde(CalificacionConductor c) {
        return new CalificacionConductorResponseDTO(
                c.getId(), c.getConductor().getId(),
                c.getPuntaje(), c.getComentario(), c.getCreadoEn()
        );
    }
}