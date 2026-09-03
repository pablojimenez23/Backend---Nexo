package com.nexo.msusers.dto;

import jakarta.validation.constraints.NotBlank;

public record RechazoConductorRequestDTO(
        @NotBlank(message = "El motivo de rechazo es obligatorio")
        String motivo
) {
}