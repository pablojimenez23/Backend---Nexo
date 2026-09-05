package com.nexo.msstores.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record TiendaRequestDTO(
        @NotBlank(message = "El nombre es obligatorio")
        String nombre,

        String descripcion,

        @NotBlank(message = "La dirección es obligatoria")
        String direccion,

        BigDecimal latitud,
        BigDecimal longitud,
        String logoUrl,

        @NotBlank(message = "El horario es obligatorio")
        @Pattern(
                regexp = "^([01]?\\d|2[0-3]):[0-5]\\d\\s*-\\s*([01]?\\d|2[0-3]):[0-5]\\d$",
                message = "El horario debe tener el formato HH:MM-HH:MM (ej: 12:00-23:00)"
        )
        String horario,

        BigDecimal montoMinimo,
        String categoria
) {
}