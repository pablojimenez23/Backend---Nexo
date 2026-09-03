package com.nexo.msusers.dto;

import com.nexo.msusers.entity.Notificacion;
import java.time.Instant;
import java.util.UUID;

public record NotificacionResponseDTO(
        UUID id,
        String titulo,
        String mensaje,
        boolean leida,
        Instant creadoEn
) {
    public static NotificacionResponseDTO desde(Notificacion n) {
        return new NotificacionResponseDTO(
                n.getId(), n.getTitulo(), n.getMensaje(), n.isLeida(), n.getCreadoEn()
        );
    }
}