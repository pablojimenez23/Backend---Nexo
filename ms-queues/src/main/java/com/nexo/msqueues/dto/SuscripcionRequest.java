package com.nexo.msqueues.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Regla de distribución: la cola recibe solo los eventos listados.
 */
public record SuscripcionRequest(
        @NotBlank(message = "La cola es obligatoria")
        @Pattern(regexp = Patrones.RECURSO, message = Patrones.MENSAJE_RECURSO)
        String queue,

        @NotEmpty(message = "Debes indicar al menos un evento")
        @Size(max = 20, message = "Máximo 20 eventos por suscripción")
        List<@NotBlank(message = "Los eventos no pueden estar vacíos")
             @Pattern(regexp = Patrones.EVENTO, message = Patrones.MENSAJE_EVENTO) String> eventos) {
}