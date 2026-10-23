package com.nexo.msproducts.dto;

import com.nexo.msproducts.entity.Oferta;
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
        boolean disponible,
        OfertaResponseDTO oferta
) {
    public static ProductoResponseDTO desde(Producto p) {
        return desde(p, null);
    }

    /** oferta = la oferta vigente del producto, o null si no tiene. */
    public static ProductoResponseDTO desde(Producto p, Oferta oferta) {
        return new ProductoResponseDTO(
                p.getId(), p.getTiendaId(), p.getCategoriaId(), p.getNombre(),
                p.getDescripcion(), p.getPrecio(), p.getStock(), p.getImagenUrl(),
                p.isDisponible(),
                oferta == null ? null : OfertaResponseDTO.desde(oferta, p.getPrecio())
        );
    }
}