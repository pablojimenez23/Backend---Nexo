package com.nexo.msproducts.dto;

import com.nexo.msproducts.entity.Oferta;
import com.nexo.msproducts.entity.Producto;
import com.nexo.msproducts.entity.TipoProducto;
import com.nexo.msproducts.entity.VarianteProducto;
import com.nexo.msproducts.service.CatalogoTipos;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Con variantes, "precio" es el más bajo entre las disponibles ("desde $X") y "stock" la suma de todas.
 * La oferta, si hay una vigente, se calcula sobre el precio de cada variante.
 * requiereMayorEdad es true cuando el producto declara que contiene alcohol.
 */
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
        OfertaResponseDTO oferta,
        String tipo,
        String tipoEtiqueta,
        boolean requiereMayorEdad,
        String etiquetaVariante,
        Map<String, Object> atributos,
        List<VarianteDTO> variantes,
        List<GrupoOpcionesDTO> grupos,
        boolean destacado,
        Integer personas
) {
    public static ProductoResponseDTO desde(Producto p) {
        return desde(p, null);
    }

    /** oferta = la oferta vigente del producto, o null si no tiene. */
    public static ProductoResponseDTO desde(Producto p, Oferta oferta) {
        return desde(p, oferta, List.of(), List.of());
    }

    public static ProductoResponseDTO desde(Producto p, Oferta oferta,
                                            List<VarianteProducto> variantes, List<GrupoOpcionesDTO> grupos) {
        TipoProducto tipo = p.getTipo();
        BigDecimal precio = p.getPrecio();
        Integer stock = p.getStock();
        boolean disponible = p.isDisponible();
        Map<String, Object> atributos = CatalogoTipos.leer(p.getAtributos());

        if (!variantes.isEmpty()) {
            precio = variantes.stream()
                    .filter(VarianteProducto::isDisponible)
                    .map(VarianteProducto::getPrecio)
                    .min(Comparator.naturalOrder())
                    .orElseGet(() -> variantes.stream()
                            .map(VarianteProducto::getPrecio)
                            .min(Comparator.naturalOrder())
                            .orElse(p.getPrecio()));
            stock = variantes.stream().mapToInt(v -> v.getStock() == null ? 0 : v.getStock()).sum();
            disponible = disponible && variantes.stream().anyMatch(VarianteProducto::isDisponible);
        }

        return new ProductoResponseDTO(
                p.getId(), p.getTiendaId(), p.getCategoriaId(), p.getNombre(),
                p.getDescripcion(), precio, stock, p.getImagenUrl(),
                disponible,
                oferta == null ? null : OfertaResponseDTO.desde(oferta, precio),
                tipo.name(), tipo.getEtiqueta(), CatalogoTipos.llevaAlcohol(atributos), tipo.getEtiquetaVariante(),
                atributos,
                variantes.stream().map(v -> VarianteDTO.desde(v, oferta)).toList(),
                grupos,
                p.isDestacado(),
                p.getPersonas()
        );
    }
}