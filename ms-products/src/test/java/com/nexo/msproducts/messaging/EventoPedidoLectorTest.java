package com.nexo.msproducts.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validación de los mensajes que llegan al consumidor (error definitivo si son inválidos). */
class EventoPedidoLectorTest {

    private static final String EVENTO_ID = "11111111-1111-1111-1111-111111111111";
    private static final String PEDIDO_ID = "22222222-2222-2222-2222-222222222222";
    private static final String CLIENTE_ID = "33333333-3333-3333-3333-333333333333";
    private static final String PRODUCTO_ID = "44444444-4444-4444-4444-444444444444";

    private static final String F_EVENTO_ID = "\"eventoId\":\"" + EVENTO_ID + "\"";
    private static final String F_EVENTO = "\"evento\":\"cancelado\"";
    private static final String F_PEDIDO = "\"pedidoId\":\"" + PEDIDO_ID + "\"";

    private final EventoPedidoLector lector = new EventoPedidoLector();

    private static String json(String... campos) {
        return "{" + String.join(",", campos) + "}";
    }

    // ---------- mensajes válidos ----------

    @Test
    @DisplayName("Mensaje completo: se convierte en EventoPedido con todos sus campos")
    void mensajeCompleto() {
        String cuerpo = json(F_EVENTO_ID, F_EVENTO, F_PEDIDO,
                "\"clienteId\":\"" + CLIENTE_ID + "\"",
                "\"total\":15990",
                "\"items\":[{\"productoId\":\"" + PRODUCTO_ID + "\",\"cantidad\":2}]");

        EventoPedido e = lector.leer(cuerpo);

        assertEquals(UUID.fromString(EVENTO_ID), e.eventoId());
        assertEquals("cancelado", e.evento());
        assertEquals(UUID.fromString(PEDIDO_ID), e.pedidoId());
        assertEquals(UUID.fromString(CLIENTE_ID), e.clienteId());
        assertEquals(0, new BigDecimal("15990").compareTo(e.total()));
        assertEquals(1, e.items().size());
        assertEquals(UUID.fromString(PRODUCTO_ID), e.items().get(0).productoId());
        assertEquals(2, e.items().get(0).cantidad());
    }

    @Test
    @DisplayName("Solo los campos obligatorios: los opcionales quedan nulos y los items vacíos")
    void soloObligatorios() {
        EventoPedido e = lector.leer(json(F_EVENTO_ID, F_EVENTO, F_PEDIDO));

        assertNull(e.clienteId());
        assertNull(e.tiendaId());
        assertNull(e.conductorId());
        assertNull(e.ownerId());
        assertNull(e.total());
        assertTrue(e.items().isEmpty());
    }

    // ---------- mensajes inválidos ----------

    @ParameterizedTest(name = "[{index}] {1}")
    @MethodSource("mensajesInvalidos")
    @DisplayName("Mensaje inválido: se rechaza con un motivo claro")
    void mensajeInvalido_seRechaza(String cuerpo, String motivoEsperado) {
        MensajeInvalidoException ex = assertThrows(MensajeInvalidoException.class, () -> lector.leer(cuerpo));

        assertTrue(ex.getMessage().contains(motivoEsperado),
                "Motivo recibido: " + ex.getMessage());
    }

    static Stream<Arguments> mensajesInvalidos() {
        return Stream.of(
                Arguments.of("", "Mensaje vacío"),
                Arguments.of("esto no es json", "JSON inválido"),
                Arguments.of(json(F_EVENTO_ID, F_EVENTO, F_PEDIDO) + " basura", "texto sobrante"),
                Arguments.of("[1,2]", "no es un objeto JSON"),
                Arguments.of(json(F_EVENTO_ID, F_PEDIDO), "Falta el campo 'evento'"),
                Arguments.of(json(F_EVENTO_ID, "\"evento\":\"  \"", F_PEDIDO), "Falta el campo 'evento'"),
                Arguments.of(json(F_EVENTO, F_PEDIDO), "Falta el campo 'eventoId'"),
                Arguments.of(json("\"eventoId\":\"no-es-uuid\"", F_EVENTO, F_PEDIDO), "no es un UUID válido"),
                Arguments.of(json("\"eventoId\":123", F_EVENTO, F_PEDIDO), "debe ser texto"),
                Arguments.of(json(F_EVENTO_ID, F_EVENTO), "Falta el campo 'pedidoId'"),
                Arguments.of(json(F_EVENTO_ID, F_EVENTO, F_PEDIDO, "\"total\":\"abc\""), "'total' debe ser numérico"),
                Arguments.of(json(F_EVENTO_ID, F_EVENTO, F_PEDIDO, "\"items\":\"abc\""), "debe ser una lista"),
                Arguments.of(json(F_EVENTO_ID, F_EVENTO, F_PEDIDO, "\"items\":[1]"), "Cada item debe ser un objeto"),
                Arguments.of(json(F_EVENTO_ID, F_EVENTO, F_PEDIDO,
                        "\"items\":[{\"cantidad\":1}]"), "Falta el campo 'productoId'"),
                Arguments.of(json(F_EVENTO_ID, F_EVENTO, F_PEDIDO,
                        "\"items\":[{\"productoId\":\"" + PRODUCTO_ID + "\"}]"), "cantidad del item debe ser numérica"),
                Arguments.of(json(F_EVENTO_ID, F_EVENTO, F_PEDIDO,
                        "\"items\":[{\"productoId\":\"" + PRODUCTO_ID + "\",\"cantidad\":0}]"), "mayor a 0"),
                Arguments.of(json(F_EVENTO_ID, F_EVENTO, F_PEDIDO,
                        "\"items\":[{\"productoId\":\"" + PRODUCTO_ID + "\",\"cantidad\":1.5}]"), "debe ser un entero")
        );
    }
}