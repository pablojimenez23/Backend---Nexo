package com.nexo.msproducts.dto;

import com.nexo.msproducts.entity.GrupoOpciones;
import com.nexo.msproducts.entity.OpcionProducto;

import java.util.List;
import java.util.UUID;

/**
 * Sirve para enviar y para recibir. "obligatorio" solo se llena al responder (es minimo >= 1).
 * Al recibir: minimo vacío = 0 (opcional); maximo vacío = el mayor entre 1 y el mínimo.
 */
public record GrupoOpcionesDTO(
        UUID id,
        String nombre,
        Integer minimo,
        Integer maximo,
        Boolean obligatorio,
        List<OpcionDTO> opciones
) {
    public GrupoOpcionesDTO(UUID id, String nombre, Integer minimo, Integer maximo, List<OpcionDTO> opciones) {
        this(id, nombre, minimo, maximo, null, opciones);
    }

    public static GrupoOpcionesDTO desde(GrupoOpciones g, List<OpcionProducto> opciones) {
        return new GrupoOpcionesDTO(
                g.getId(), g.getNombre(), g.getMinimo(), g.getMaximo(), g.isObligatorio(),
                opciones.stream().map(OpcionDTO::desde).toList()
        );
    }
}