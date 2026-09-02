package com.nexo.msusers.dto;

import com.nexo.msusers.entity.Usuario;
import java.util.UUID;

public record UsuarioResponseDTO(
        UUID id,
        String email,
        String nombre,
        String rol,
        String estado
) {
    public static UsuarioResponseDTO desde(Usuario usuario) {
        return new UsuarioResponseDTO(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNombre(),
                usuario.getRol().name(),
                usuario.getEstado().name()
        );
    }
}