package com.nexo.msusers.dto;

import java.util.UUID;

public record CrearNotificacionRequestDTO(
        UUID usuarioId,
        String titulo,
        String mensaje
) {
}