package com.nexo.msorders.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelarPedidoRequestDTO(
        @NotBlank(message = "El motivo de cancelación es obligatorio")
        String motivo
) {
}