package com.nexo.msusers.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CalificacionConductorRequestDTO(
        @NotNull UUID pedidoId,
        @NotNull @Min(1) @Max(5) Integer puntaje,
        String comentario
) {
}