package com.nexo.msqueues.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Mensaje de prueba que se envía directo a una cola.
 */
public record MensajeRequest(
        @NotBlank(message = "El cuerpo del mensaje es obligatorio")
        @Size(max = 5000, message = "El cuerpo del mensaje no puede superar 5000 caracteres")
        String body) {
}