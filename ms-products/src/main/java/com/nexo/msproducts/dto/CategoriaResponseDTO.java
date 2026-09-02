package com.nexo.msproducts.dto;

import com.nexo.msproducts.entity.Categoria;
import java.util.UUID;

public record CategoriaResponseDTO(UUID id, String nombre) {
    public static CategoriaResponseDTO desde(Categoria c) {
        return new CategoriaResponseDTO(c.getId(), c.getNombre());
    }
}