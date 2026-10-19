package com.nexo.msqueues.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Modificación parcial de una cola: solo se cambia lo que venga informado.
 */
public record QueueUpdateRequest(
        @Min(value = 0, message = "visibilityTimeoutSeconds mínimo 0")
        @Max(value = 43200, message = "visibilityTimeoutSeconds máximo 43200 (12 horas)")
        Integer visibilityTimeoutSeconds,

        @Min(value = 60, message = "retentionSeconds mínimo 60")
        @Max(value = 1209600, message = "retentionSeconds máximo 1209600 (14 días)")
        Integer retentionSeconds,

        @Min(value = 1, message = "maxReceiveCount mínimo 1")
        @Max(value = 10, message = "maxReceiveCount máximo 10")
        Integer maxReceiveCount) {
}