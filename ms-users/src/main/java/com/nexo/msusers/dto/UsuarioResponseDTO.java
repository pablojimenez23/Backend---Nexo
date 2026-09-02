package com.nexo.msusers.dto;

import com.nexo.msusers.entity.Usuario;
import java.time.Instant;
import java.util.UUID;

public record UsuarioResponseDTO(
        UUID id,
        String email,
        String nombre,
        String pictureUrl,
        String rol,
        String estado,
        Instant creadoEn
) {
    public static UsuarioResponseDTO desde(Usuario usuario) {
        return new UsuarioResponseDTO(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNombre(),
                usuario.getPictureUrl(),
                usuario.getRol().name(),
                usuario.getEstado().name(),
                usuario.getCreadoEn()
        );
    }
}