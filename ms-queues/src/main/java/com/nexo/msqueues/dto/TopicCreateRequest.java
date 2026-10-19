package com.nexo.msqueues.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TopicCreateRequest(
        @NotBlank(message = "El nombre del topic es obligatorio")
        @Size(min = 5, max = 60, message = "El nombre debe tener entre 5 y 60 caracteres")
        @Pattern(regexp = Patrones.RECURSO, message = Patrones.MENSAJE_RECURSO)
        String name) {
}