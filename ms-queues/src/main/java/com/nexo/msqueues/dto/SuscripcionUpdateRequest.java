package com.nexo.msqueues.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SuscripcionUpdateRequest(
        @NotEmpty(message = "Debes indicar al menos un evento")
        @Size(max = 20, message = "Máximo 20 eventos por suscripción")
        List<@NotBlank(message = "Los eventos no pueden estar vacíos")
             @Pattern(regexp = Patrones.EVENTO, message = Patrones.MENSAJE_EVENTO) String> eventos) {
}