package com.nexo.msproducts.dto;

import com.nexo.msproducts.entity.Producto;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductoResponseDTO(
        UUID id,
        UUID tiendaId,
        UUID categoriaId,
        String nombre,
        String descripcion,
        BigDecimal precio,
        Integer stock,
        String imagenUrl,
        boolean disponible
) {
    public static ProductoResponseDTO desde(Producto p) {
        return new ProductoResponseDTO(
                p.getId(), p.getTiendaId(), p.getCategoriaId(), p.getNombre(),
                p.getDescripcion(), p.getPrecio(), p.getStock(), p.getImagenUrl(),
                p.isDisponible()
        );
    }
}