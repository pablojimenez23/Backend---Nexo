package com.nexo.msqueues.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Evento de prueba publicado en un topic para comprobar a qué colas llega.
 */
public record PublicarRequest(
        @NotBlank(message = "El evento es obligatorio")
        @Pattern(regexp = Patrones.EVENTO, message = Patrones.MENSAJE_EVENTO)
        String evento,

        @NotBlank(message = "El mensaje es obligatorio")
        @Size(max = 5000, message = "El mensaje no puede superar 5000 caracteres")
        String mensaje) {
}