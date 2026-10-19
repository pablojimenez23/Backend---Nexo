package com.nexo.msproducts.messaging;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Convierte el cuerpo del mensaje en un EventoPedido y lo valida. */
@Component
public class EventoPedidoLector {

    public EventoPedido leer(String cuerpo) {
        Object raw = MiniJson.parse(cuerpo);
        if (!(raw instanceof Map<?, ?> m)) {
            throw new MensajeInvalidoException("El mensaje no es un objeto JSON");
        }

        Object evento = m.get("evento");
        if (!(evento instanceof String nombre) || nombre.isBlank()) {
            throw new MensajeInvalidoException("Falta el campo 'evento'");
        }

        return new EventoPedido(
                uuid(m, "eventoId", true),
                nombre,
                uuid(m, "pedidoId", true),
                uuid(m, "clienteId", false),
                uuid(m, "tiendaId", false),
                uuid(m, "conductorId", false),
                uuid(m, "ownerId", false),
                total(m),
                items(m));
    }

    private UUID uuid(Map<?, ?> m, String campo, boolean requerido) {
        Object v = m.get(campo);
        if (v == null) {
            if (requerido) {
                throw new MensajeInvalidoException("Falta el campo '" + campo + "'");
            }
            return null;
        }
        if (!(v instanceof String texto)) {
            throw new MensajeInvalidoException("El campo '" + campo + "' debe ser texto");
        }
        try {
            return UUID.fromString(texto);
        } catch (IllegalArgumentException e) {
            throw new MensajeInvalidoException("El campo '" + campo + "' no es un UUID válido");
        }
    }

    private BigDecimal total(Map<?, ?> m) {
        Object v = m.get("total");
        if (v == null) {
            return null;
        }
        if (!(v instanceof BigDecimal numero)) {
            throw new MensajeInvalidoException("El campo 'total' debe ser numérico");
        }
        return numero;
    }

    private List<EventoPedido.Item> items(Map<?, ?> m) {
        Object v = m.get("items");
        if (v == null) {
            return List.of();
        }
        if (!(v instanceof List<?> lista)) {
            throw new MensajeInvalidoException("El campo 'items' debe ser una lista");
        }
        List<EventoPedido.Item> resultado = new ArrayList<>();
        for (Object elemento : lista) {
            if (!(elemento instanceof Map<?, ?> item)) {
                throw new MensajeInvalidoException("Cada item debe ser un objeto");
            }
            UUID productoId = uuid(item, "productoId", true);
            Object cant = item.get("cantidad");
            if (!(cant instanceof BigDecimal bd)) {
                throw new MensajeInvalidoException("La cantidad del item debe ser numérica");
            }
            int cantidad;
            try {
                cantidad = bd.intValueExact();
            } catch (ArithmeticException e) {
                throw new MensajeInvalidoException("La cantidad del item debe ser un entero");
            }
            if (cantidad <= 0) {
                throw new MensajeInvalidoException("La cantidad del item debe ser mayor a 0");
            }
            resultado.add(new EventoPedido.Item(productoId, cantidad));
        }
        return resultado;
    }
}