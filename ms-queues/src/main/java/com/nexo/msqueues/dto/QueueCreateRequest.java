package com.nexo.msqueues.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record QueueCreateRequest(
        @NotBlank(message = "El nombre de la cola es obligatorio")
        @Size(min = 5, max = 60, message = "El nombre debe tener entre 5 y 60 caracteres")
        @Pattern(regexp = Patrones.RECURSO, message = Patrones.MENSAJE_RECURSO)
        String name,

        @Size(max = 70, message = "El nombre de la DLQ no puede superar 70 caracteres")
        @Pattern(regexp = "^(nexo-[a-z0-9-]+-dlq)?$",
                 message = "La DLQ debe llamarse 'nexo-...-dlq' (minúsculas, números y guiones)")
        String dlqName,

        @Min(value = 1, message = "maxReceiveCount mínimo 1")
        @Max(value = 10, message = "maxReceiveCount máximo 10")
        Integer maxReceiveCount,

        @Min(value = 0, message = "visibilityTimeoutSeconds mínimo 0")
        @Max(value = 43200, message = "visibilityTimeoutSeconds máximo 43200 (12 horas)")
        Integer visibilityTimeoutSeconds,

        @Min(value = 60, message = "retentionSeconds mínimo 60")
        @Max(value = 1209600, message = "retentionSeconds máximo 1209600 (14 días)")
        Integer retentionSeconds) {
}