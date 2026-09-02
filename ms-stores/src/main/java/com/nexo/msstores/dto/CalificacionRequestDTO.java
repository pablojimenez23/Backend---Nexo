package com.nexo.msstores.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CalificacionRequestDTO(
        @NotNull @Min(1) @Max(5)
        Integer puntaje,
        String comentario
) {
}