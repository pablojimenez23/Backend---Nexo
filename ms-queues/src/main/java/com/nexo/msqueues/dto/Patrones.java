package com.nexo.msqueues.dto;

/**
 * Reglas de nombres compartidas por las validaciones de rutas y cuerpos de solicitud.
 */
public final class Patrones {

    /** Colas y topics: siempre con prefijo "nexo-" para no tocar recursos ajenos. */
    public static final String RECURSO = "^nexo-[a-z0-9-]+$";
    public static final String MENSAJE_RECURSO =
            "El nombre debe empezar con 'nexo-' y usar solo minúsculas, números y guiones";

    /** Eventos: por ejemplo pedido.pagado o pedido.cancelado. */
    public static final String EVENTO = "^[a-z0-9]+([._-][a-z0-9]+)*$";
    public static final String MENSAJE_EVENTO =
            "Evento inválido: usa minúsculas, números y los separadores . _ -";

    private Patrones() {
    }
}