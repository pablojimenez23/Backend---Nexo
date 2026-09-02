package com.nexo.msstores.dto;

import com.nexo.msstores.entity.Calificacion;
import java.time.Instant;
import java.util.UUID;

public record CalificacionResponseDTO(
        UUID id,
        UUID tiendaId,
        UUID usuarioId,
        Integer puntaje,
        String comentario,
        Instant creadoEn
) {
    public static CalificacionResponseDTO desde(Calificacion c) {
        return new CalificacionResponseDTO(
                c.getId(), c.getTienda().getId(), c.getUsuarioId(),
                c.getPuntaje(), c.getComentario(), c.getCreadoEn()
        );
    }
}