package com.nexo.msproducts.dto;

import jakarta.validation.constraints.NotBlank;

public record CategoriaRequestDTO(
        @NotBlank(message = "El nombre de la categoría es obligatorio")
        String nombre
) {
}