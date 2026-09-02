package com.nexo.msstores.dto;

import jakarta.validation.constraints.NotBlank;

public record RechazoRequestDTO(
        @NotBlank(message = "El motivo de rechazo es obligatorio")
        String motivo
) {
}