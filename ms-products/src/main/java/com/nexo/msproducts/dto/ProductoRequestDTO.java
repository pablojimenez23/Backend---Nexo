package com.nexo.msproducts.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Todo lo posterior a "disponible" es opcional: la app antigua sigue funcionando sin mandarlo.
 * - variantes / grupos == null  -> no se tocan los que ya tiene el producto.
 * - variantes / grupos == []    -> se borran todos (el producto vuelve a ser simple).
 * - con datos                   -> reemplazan a los actuales (los que traen id se actualizan).
 * - personas == 0               -> borra el dato.
 */
public record ProductoRequestDTO(
        UUID tiendaId,
        String nombre,
        String descripcion,
        BigDecimal precio,
        Integer stock,
        Boolean disponible,
        String tipo,
        Map<String, Object> atributos,
        List<VarianteDTO> variantes,
        List<GrupoOpcionesDTO> grupos,
        Boolean destacado,
        Integer personas
) {
    public ProductoRequestDTO(UUID tiendaId, String nombre, String descripcion,
                              BigDecimal precio, Integer stock, Boolean disponible) {
        this(tiendaId, nombre, descripcion, precio, stock, disponible, null, null, null, null, null, null);
    }

    public ProductoRequestDTO(UUID tiendaId, String nombre, String descripcion,
                              BigDecimal precio, Integer stock, Boolean disponible,
                              String tipo, Map<String, Object> atributos,
                              List<VarianteDTO> variantes, List<GrupoOpcionesDTO> grupos) {
        this(tiendaId, nombre, descripcion, precio, stock, disponible, tipo, atributos, variantes, grupos, null, null);
    }
}