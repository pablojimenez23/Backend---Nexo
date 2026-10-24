package com.nexo.msproducts.service;

import com.nexo.msproducts.entity.TipoProducto;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CatalogoTiposTest {

    private final CatalogoTipos catalogo = new CatalogoTipos();

    @Test
    void pizza_sinIngredientes_lanza400() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> catalogo.normalizar(TipoProducto.PIZZA, Map.of("vegetariano", true)));
        assertEquals(400, e.getStatusCode().value());
    }

    @Test
    void pizza_ingredientesSeLimpianYSinRepetidos() {
        String json = catalogo.normalizar(TipoProducto.PIZZA, Map.of(
                "ingredientes", List.of(" Tomate ", "tomate", "Mozzarella", "  "),
                "vegetariano", true));

        Map<String, Object> datos = CatalogoTipos.leer(json);
        assertEquals(List.of("Tomate", "Mozzarella"), datos.get("ingredientes"));
        assertEquals(true, datos.get("vegetariano"));
    }

    @Test
    void ingredientesComoTextoSeSeparanPorComas() {
        String json = catalogo.normalizar(TipoProducto.HAMBURGUESA, Map.of("ingredientes", "Carne, Queso, Lechuga"));

        assertEquals(List.of("Carne", "Queso", "Lechuga"), CatalogoTipos.leer(json).get("ingredientes"));
    }

    @Test
    void empanada_sinRelleno_lanza400() {
        assertThrows(ResponseStatusException.class,
                () -> catalogo.normalizar(TipoProducto.EMPANADA, Map.of("alergenos", List.of("Gluten"))));
    }

    @Test
    void numeroFueraDeRango_lanza400() {
        assertThrows(ResponseStatusException.class,
                () -> catalogo.normalizar(TipoProducto.BEBIDA, Map.of("volumen_ml", 99999)));
    }

    @Test
    void numeroComoTextoSeConvierte() {
        String json = catalogo.normalizar(TipoProducto.BEBIDA, Map.of("volumen_ml", "500"));

        assertEquals(500, ((Number) CatalogoTipos.leer(json).get("volumen_ml")).intValue());
    }

    @Test
    void booleanoInvalido_lanza400() {
        assertThrows(ResponseStatusException.class,
                () -> catalogo.normalizar(TipoProducto.BEBIDA, Map.of("sin_azucar", "quizás")));
    }

    @Test
    void clavesQueNoSonDelTipoSeDescartan() {
        String json = catalogo.normalizar(TipoProducto.BEBIDA,
                Map.of("sin_azucar", true, "ingredientes", List.of("Tomate"), "masa", "Delgada"));

        Map<String, Object> datos = CatalogoTipos.leer(json);
        assertFalse(datos.containsKey("ingredientes"));
        assertFalse(datos.containsKey("masa"));
        assertEquals(true, datos.get("sin_azucar"));
    }

    @Test
    void tipoOtro_sinDatos_devuelveNull() {
        assertNull(catalogo.normalizar(TipoProducto.OTRO, Map.of("lo_que_sea", "x")));
        assertNull(catalogo.normalizar(TipoProducto.OTRO, null));
    }

    @Test
    void elementoDeListaMuyLargo_lanza400() {
        assertThrows(ResponseStatusException.class, () -> catalogo.normalizar(TipoProducto.PIZZA,
                Map.of("ingredientes", List.of("x".repeat(41)))));
    }

    @Test
    void leer_jsonDanadoOVacio_devuelveMapaVacio() {
        assertTrue(CatalogoTipos.leer(null).isEmpty());
        assertTrue(CatalogoTipos.leer("").isEmpty());
        assertTrue(CatalogoTipos.leer("{esto no es json").isEmpty());
    }

    @Test
    void llevaAlcohol_soloConLaMarcaExplicita() {
        String json = catalogo.normalizar(TipoProducto.BEBIDA, Map.of("contiene_alcohol", true, "volumen_ml", 750));

        assertTrue(CatalogoTipos.llevaAlcohol(CatalogoTipos.leer(json)));
        assertFalse(CatalogoTipos.llevaAlcohol(CatalogoTipos.leer(
                catalogo.normalizar(TipoProducto.BEBIDA, Map.of("sin_azucar", true)))));
        assertFalse(CatalogoTipos.llevaAlcohol(null));
    }

    @Test
    void catalogo_incluyeTodosLosTiposSoloDeDelivery() {
        List<CatalogoTipos.TipoInfo> tipos = catalogo.catalogo();

        assertEquals(TipoProducto.values().length, tipos.size());
        assertTrue(tipos.stream().anyMatch(t -> t.clave().equals("SUSHI")));
        assertTrue(tipos.stream().noneMatch(t -> t.clave().equals("VINO") || t.clave().equals("CERVEZA")));
    }

    @Test
    void catalogo_pizzaSugiereTamanosYMasaObligatoria() {
        CatalogoTipos.TipoInfo pizza = catalogo.catalogo().stream()
                .filter(t -> t.clave().equals("PIZZA")).findFirst().orElseThrow();

        assertEquals(List.of("Personal", "Mediana", "Familiar"), pizza.variantesSugeridas());
        CatalogoTipos.Sugerencia masa = pizza.gruposSugeridos().stream()
                .filter(g -> g.nombre().equals("Masa")).findFirst().orElseThrow();
        assertEquals(1, masa.minimo());
        assertEquals(1, masa.maximo());
        assertTrue(masa.opciones().contains("Delgada"));
    }

    @Test
    void catalogo_otroNoSugiereNada() {
        CatalogoTipos.TipoInfo otro = catalogo.catalogo().stream()
                .filter(t -> t.clave().equals("OTRO")).findFirst().orElseThrow();

        assertTrue(otro.variantesSugeridas().isEmpty());
        assertTrue(otro.gruposSugeridos().isEmpty());
        assertTrue(otro.campos().isEmpty());
    }
}