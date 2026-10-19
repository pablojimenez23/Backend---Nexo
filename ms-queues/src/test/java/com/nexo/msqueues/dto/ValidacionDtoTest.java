package com.nexo.msqueues.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Validación de las solicitudes (indicador: validación de solicitudes). No necesita AWS ni Spring. */
class ValidacionDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private QueueCreateRequest cola(String name, String dlq, Integer max, Integer vis, Integer ret) {
        return new QueueCreateRequest(name, dlq, max, vis, ret);
    }

    @Test
    void colaValidaNoTieneErrores() {
        assertThat(validator.validate(cola("nexo-pedidos", "nexo-pedidos-dlq", 3, 30, 345600))).isEmpty();
    }

    @Test
    void colaSoloConNombreEsValida() {
        assertThat(validator.validate(cola("nexo-pedidos", null, null, null, null))).isEmpty();
    }

    @Test
    void nombreVacioEsInvalido() {
        assertThat(validator.validate(cola("", null, null, null, null))).isNotEmpty();
    }

    @Test
    void nombreSinPrefijoNexoEsInvalido() {
        assertThat(validator.validate(cola("pedidos-cola", null, null, null, null))).isNotEmpty();
    }

    @Test
    void nombreConMayusculasOEspaciosEsInvalido() {
        assertThat(validator.validate(cola("nexo-Pedidos", null, null, null, null))).isNotEmpty();
        assertThat(validator.validate(cola("nexo pedidos", null, null, null, null))).isNotEmpty();
    }

    @Test
    void nombreMuyLargoEsInvalido() {
        assertThat(validator.validate(cola("nexo-" + "a".repeat(60), null, null, null, null))).isNotEmpty();
    }

    @Test
    void dlqSinSufijoDlqEsInvalida() {
        assertThat(validator.validate(cola("nexo-pedidos", "nexo-errores", null, null, null))).isNotEmpty();
    }

    @Test
    void maxReceiveCountFueraDeRangoEsInvalido() {
        assertThat(validator.validate(cola("nexo-pedidos", null, 0, null, null))).isNotEmpty();
        assertThat(validator.validate(cola("nexo-pedidos", null, 11, null, null))).isNotEmpty();
        assertThat(validator.validate(cola("nexo-pedidos", null, 1, null, null))).isEmpty();
        assertThat(validator.validate(cola("nexo-pedidos", null, 10, null, null))).isEmpty();
    }

    @Test
    void visibilidadYRetencionFueraDeRangoSonInvalidas() {
        assertThat(validator.validate(cola("nexo-pedidos", null, null, -1, null))).isNotEmpty();
        assertThat(validator.validate(cola("nexo-pedidos", null, null, 43201, null))).isNotEmpty();
        assertThat(validator.validate(cola("nexo-pedidos", null, null, null, 59))).isNotEmpty();
        assertThat(validator.validate(cola("nexo-pedidos", null, null, null, 1209601))).isNotEmpty();
    }

    @Test
    void modificacionValidaEInvalida() {
        assertThat(validator.validate(new QueueUpdateRequest(60, 3600, 5))).isEmpty();
        assertThat(validator.validate(new QueueUpdateRequest(null, null, null))).isEmpty(); // lo rechaza el servicio
        assertThat(validator.validate(new QueueUpdateRequest(-5, null, null))).isNotEmpty();
        assertThat(validator.validate(new QueueUpdateRequest(null, null, 99))).isNotEmpty();
    }

    @Test
    void mensajeVacioOMuyGrandeEsInvalido() {
        assertThat(validator.validate(new MensajeRequest("{\"evento\":\"prueba\"}"))).isEmpty();
        assertThat(validator.validate(new MensajeRequest(""))).isNotEmpty();
        assertThat(validator.validate(new MensajeRequest(null))).isNotEmpty();
        assertThat(validator.validate(new MensajeRequest("x".repeat(5001)))).isNotEmpty();
    }

    @Test
    void suscripcionValida() {
        assertThat(validator.validate(new SuscripcionRequest("nexo-stock", List.of("pedido.cancelado")))).isEmpty();
        assertThat(validator.validate(new SuscripcionRequest("nexo-puntos",
                List.of("pedido.completado", "pedido.entregado")))).isEmpty();
    }

    @Test
    void suscripcionInvalida() {
        assertThat(validator.validate(new SuscripcionRequest("nexo-stock", List.of()))).isNotEmpty();
        assertThat(validator.validate(new SuscripcionRequest("nexo-stock", null))).isNotEmpty();
        assertThat(validator.validate(new SuscripcionRequest("nexo-stock", List.of("Pedido Cancelado")))).isNotEmpty();
        assertThat(validator.validate(new SuscripcionRequest("nexo-stock", List.of("")))).isNotEmpty();
        assertThat(validator.validate(new SuscripcionRequest("stock", List.of("pedido.cancelado")))).isNotEmpty();
        List<String> muchos = java.util.stream.IntStream.range(0, 21).mapToObj(i -> "evento" + i).toList();
        assertThat(validator.validate(new SuscripcionRequest("nexo-stock", muchos))).isNotEmpty();
    }
}