package com.nexo.msproducts.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.msproducts.entity.TipoProducto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Define qué datos informativos pide cada tipo de producto (ingredientes, alérgenos, etiquetas de dieta)
 * y valida lo que manda la tienda. Se guardan como JSON en Producto.atributos.
 *
 * Lo que el cliente DECIDE al comprar (tamaño, masa, punto de la carne, extras...) no va acá:
 * va en variantes y grupos de opciones. Por eso el catálogo también entrega sugerencias de
 * variantes y de grupos para que la tienda no parta de cero.
 */
@Component
public class CatalogoTipos {

    public enum TipoCampo { TEXTO, NUMERO, LISTA, BOOLEANO, OPCION }

    public record Campo(String clave, String etiqueta, TipoCampo tipo, boolean obligatorio,
                        List<String> opciones, Double min, Double max) {
    }

    /** Grupo de opciones sugerido para un tipo (la tienda lo edita y le pone precios). */
    public record Sugerencia(String nombre, int minimo, int maximo, List<String> opciones) {
    }

    public record TipoInfo(String clave, String etiqueta, String etiquetaVariante, List<Campo> campos,
                           List<String> variantesSugeridas, List<Sugerencia> gruposSugeridos) {
    }

    /** Clave del atributo que marca un producto con alcohol (exige confirmar mayoría de edad). */
    public static final String CAMPO_ALCOHOL = "contiene_alcohol";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_TEXTO = 100;
    private static final int MAX_ITEM_LISTA = 40;
    private static final int MAX_ITEMS_LISTA = 20;

    private final Map<TipoProducto, List<Campo>> camposPorTipo = new EnumMap<>(TipoProducto.class);
    private final Map<TipoProducto, List<String>> variantesPorTipo = new EnumMap<>(TipoProducto.class);
    private final Map<TipoProducto, List<Sugerencia>> gruposPorTipo = new EnumMap<>(TipoProducto.class);

    public CatalogoTipos() {
        // ---------- datos informativos ----------
        campos(TipoProducto.PIZZA, ingredientes(true), alergenos(), vegetariano(), vegano(), sinGluten(), picante());
        campos(TipoProducto.HAMBURGUESA, ingredientes(true), alergenos(), vegetariano(), vegano(), sinGluten(), picante());
        campos(TipoProducto.SUSHI, ingredientes(true), alergenos(), vegetariano(), vegano(), sinGluten(), picante());
        campos(TipoProducto.SANDWICH, ingredientes(true), alergenos(), vegetariano(), vegano(), sinGluten(), picante());
        campos(TipoProducto.EMPANADA, listaDe("relleno", "Relleno", true), alergenos(),
                vegetariano(), vegano(), sinGluten(), picante());
        campos(TipoProducto.POLLO, ingredientes(false), alergenos(), sinGluten(), picante());
        campos(TipoProducto.PASTA, ingredientes(true), alergenos(), vegetariano(), vegano(), sinGluten(), picante());
        campos(TipoProducto.PLATO, ingredientes(true), listaDe("incluye", "Incluye", false), alergenos(),
                vegetariano(), vegano(), sinGluten(), picante());
        campos(TipoProducto.ENSALADA, ingredientes(true), alergenos(), vegetariano(), vegano(), sinGluten());
        campos(TipoProducto.POSTRE, ingredientes(false), alergenos(), vegetariano(), vegano(), sinGluten());
        campos(TipoProducto.CAFE, ingredientes(false), alergenos(), vegano(), sinGluten());
        campos(TipoProducto.BEBIDA,
                numeroDe("volumen_ml", "Volumen (ml)", false, 50, 5000),
                booleanoDe("sin_azucar", "Sin azúcar"),
                booleanoDe("retornable", "Envase retornable"),
                booleanoDe(CAMPO_ALCOHOL, "Contiene alcohol (+18)"),
                numeroDe("grado_alcoholico", "Grado alcohólico (%)", false, 0, 30));
        campos(TipoProducto.OTRO);

        // ---------- variantes sugeridas (tamaños, porciones, formatos) ----------
        variantes(TipoProducto.PIZZA, "Personal", "Mediana", "Familiar");
        variantes(TipoProducto.HAMBURGUESA, "Simple", "Doble", "Triple");
        variantes(TipoProducto.SUSHI, "10 piezas", "20 piezas", "30 piezas", "40 piezas");
        variantes(TipoProducto.SANDWICH, "Chico", "Grande");
        variantes(TipoProducto.EMPANADA, "1 unidad", "3 unidades", "Docena");
        variantes(TipoProducto.POLLO, "1/4 pollo", "1/2 pollo", "Pollo entero");
        variantes(TipoProducto.PASTA, "Individual", "Para compartir");
        variantes(TipoProducto.PLATO, "Normal", "Grande");
        variantes(TipoProducto.ENSALADA, "Individual", "Para compartir");
        variantes(TipoProducto.POSTRE, "Individual", "Para compartir");
        variantes(TipoProducto.CAFE, "Chico", "Mediano", "Grande");
        variantes(TipoProducto.BEBIDA, "350 ml", "500 ml", "1,5 L");
        variantes(TipoProducto.OTRO);

        // ---------- grupos de opciones sugeridos (lo que el cliente decide) ----------
        grupos(TipoProducto.PIZZA,
                grupo("Masa", 1, 1, "Tradicional", "Delgada", "Gruesa"),
                grupo("Extras", 0, 5, "Queso extra", "Aceitunas", "Champiñones", "Tocino"));
        grupos(TipoProducto.HAMBURGUESA,
                grupo("Punto de la carne", 1, 1, "Jugoso", "Término medio", "Bien cocido"),
                grupo("Acompañamiento", 1, 1, "Papas fritas", "Ensalada", "Sin acompañamiento"),
                grupo("Agregados", 0, 5, "Queso extra", "Tocino", "Palta", "Huevo"));
        grupos(TipoProducto.SUSHI,
                grupo("Salsas", 0, 3, "Soya", "Agridulce", "Spicy mayo", "Wasabi", "Jengibre"));
        grupos(TipoProducto.SANDWICH,
                grupo("Pan", 1, 1, "Marraqueta", "Ciabatta", "Integral"),
                grupo("Agregados", 0, 4, "Palta", "Queso extra", "Tocino", "Huevo"));
        grupos(TipoProducto.EMPANADA,
                grupo("Salsas", 0, 3, "Pebre", "Ají", "Salsa verde"));
        grupos(TipoProducto.POLLO,
                grupo("Preparación", 1, 1, "Frito", "Al horno", "A la parrilla"),
                grupo("Acompañamiento", 1, 1, "Papas fritas", "Arroz", "Ensalada"),
                grupo("Salsas", 0, 3, "Barbacoa", "Mayonesa", "Ají"));
        grupos(TipoProducto.PASTA,
                grupo("Tipo de pasta", 1, 1, "Spaghetti", "Fettuccine", "Penne"),
                grupo("Salsa", 1, 1, "Bolognesa", "Alfredo", "Pomodoro", "Pesto"),
                grupo("Extras", 0, 3, "Queso parmesano", "Pollo", "Camarones"));
        grupos(TipoProducto.PLATO,
                grupo("Acompañamiento", 1, 1, "Arroz", "Puré", "Papas fritas", "Ensalada"));
        grupos(TipoProducto.ENSALADA,
                grupo("Aderezo", 1, 1, "Limón", "Mayonesa", "Vinagreta"),
                grupo("Agregados", 0, 3, "Pollo", "Palta", "Huevo"));
        grupos(TipoProducto.POSTRE,
                grupo("Agregados", 0, 2, "Helado", "Crema", "Salsa de chocolate"));
        grupos(TipoProducto.CAFE,
                grupo("Temperatura", 1, 1, "Caliente", "Frío"),
                grupo("Leche", 1, 1, "Entera", "Descremada", "Sin lactosa", "Vegetal"),
                grupo("Endulzante", 1, 1, "Azúcar", "Stevia", "Sin azúcar"),
                grupo("Extras", 0, 3, "Shot extra", "Crema", "Jarabe de vainilla"));
        grupos(TipoProducto.BEBIDA,
                grupo("Temperatura", 1, 1, "Helada", "Ambiente"));
        grupos(TipoProducto.OTRO);
    }

    /** Catálogo completo, para que la app arme el formulario sin tener los tipos escritos a mano. */
    public List<TipoInfo> catalogo() {
        return Arrays.stream(TipoProducto.values())
                .map(t -> new TipoInfo(t.name(), t.getEtiqueta(), t.getEtiquetaVariante(),
                        camposPorTipo.get(t), variantesPorTipo.get(t), gruposPorTipo.get(t)))
                .toList();
    }

    /** Valida y limpia los atributos. Devuelve el JSON a guardar, o null si no queda ningún dato. */
    public String normalizar(TipoProducto tipo, Map<String, Object> entrada) {
        Map<String, Object> origen = entrada == null ? Map.of() : entrada;
        Map<String, Object> limpio = new LinkedHashMap<>();

        for (Campo campo : camposPorTipo.get(tipo)) {
            Object valor = origen.get(campo.clave());
            Object normalizado = valor == null ? null : normalizarValor(campo, valor);
            if (normalizado == null) {
                if (campo.obligatorio()) {
                    throw invalida("Falta completar: " + campo.etiqueta());
                }
                continue;
            }
            limpio.put(campo.clave(), normalizado);
        }

        if (limpio.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(limpio);
        } catch (JsonProcessingException e) {
            throw invalida("Los datos del producto no son válidos");
        }
    }

    /** Lee el JSON guardado; si está vacío o dañado devuelve un mapa vacío. */
    public static Map<String, Object> leer(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }

    /** true si los atributos declaran que el producto contiene alcohol. */
    public static boolean llevaAlcohol(Map<String, Object> atributos) {
        return atributos != null && Boolean.TRUE.equals(atributos.get(CAMPO_ALCOHOL));
    }

    private Object normalizarValor(Campo campo, Object valor) {
        return switch (campo.tipo()) {
            case TEXTO -> {
                String texto = valor.toString().trim();
                if (texto.isEmpty()) {
                    yield null;
                }
                if (texto.length() > MAX_TEXTO) {
                    throw invalida(campo.etiqueta() + " es muy largo (máximo " + MAX_TEXTO + " caracteres)");
                }
                yield texto;
            }
            case NUMERO -> {
                if (valor instanceof String cadena && cadena.isBlank()) {
                    yield null;
                }
                double n = aNumero(campo, valor);
                boolean fuera = (campo.min() != null && n < campo.min())
                        || (campo.max() != null && n > campo.max());
                if (fuera) {
                    throw invalida(campo.etiqueta() + " debe estar entre "
                            + formato(campo.min()) + " y " + formato(campo.max()));
                }
                Object resultado = (n == Math.rint(n)) ? (Object) Long.valueOf((long) n) : (Object) Double.valueOf(n);
                yield resultado;
            }
            case LISTA -> {
                List<?> crudo;
                if (valor instanceof List<?> elementos) {
                    crudo = elementos;
                } else if (valor instanceof String cadena) {
                    crudo = Arrays.asList(cadena.split(","));
                } else {
                    throw invalida(campo.etiqueta() + " debe ser una lista");
                }
                Map<String, String> unicos = new LinkedHashMap<>();
                for (Object elemento : crudo) {
                    if (elemento == null) {
                        continue;
                    }
                    String item = elemento.toString().trim();
                    if (item.isEmpty()) {
                        continue;
                    }
                    if (item.length() > MAX_ITEM_LISTA) {
                        throw invalida("Cada elemento de " + campo.etiqueta()
                                + " puede tener hasta " + MAX_ITEM_LISTA + " caracteres");
                    }
                    unicos.putIfAbsent(item.toLowerCase(Locale.ROOT), item);
                }
                if (unicos.isEmpty()) {
                    yield null;
                }
                if (unicos.size() > MAX_ITEMS_LISTA) {
                    throw invalida(campo.etiqueta() + " admite hasta " + MAX_ITEMS_LISTA + " elementos");
                }
                yield new ArrayList<>(unicos.values());
            }
            case BOOLEANO -> {
                if (valor instanceof Boolean b) {
                    yield b;
                }
                String cadena = valor.toString().trim().toLowerCase(Locale.ROOT);
                if (cadena.equals("true")) {
                    yield Boolean.TRUE;
                }
                if (cadena.equals("false")) {
                    yield Boolean.FALSE;
                }
                throw invalida(campo.etiqueta() + " debe ser sí o no");
            }
            case OPCION -> {
                String elegida = valor.toString().trim();
                if (elegida.isEmpty()) {
                    yield null;
                }
                yield campo.opciones().stream()
                        .filter(o -> o.equalsIgnoreCase(elegida))
                        .findFirst()
                        .orElseThrow(() -> invalida(campo.etiqueta() + " debe ser una de estas: "
                                + String.join(", ", campo.opciones())));
            }
        };
    }

    private static double aNumero(Campo campo, Object valor) {
        double n;
        if (valor instanceof Number numero) {
            n = numero.doubleValue();
        } else {
            try {
                n = Double.parseDouble(valor.toString().trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                throw invalida(campo.etiqueta() + " debe ser un número");
            }
        }
        if (Double.isNaN(n) || Double.isInfinite(n)) {
            throw invalida(campo.etiqueta() + " debe ser un número");
        }
        return n;
    }

    private static String formato(Double d) {
        if (d == null) {
            return "?";
        }
        return d == Math.rint(d) ? String.valueOf(d.longValue()) : String.valueOf(d);
    }

    // ==================== armado del catálogo ====================

    private void campos(TipoProducto tipo, Campo... campos) {
        camposPorTipo.put(tipo, List.of(campos));
    }

    private void variantes(TipoProducto tipo, String... nombres) {
        variantesPorTipo.put(tipo, List.of(nombres));
    }

    private void grupos(TipoProducto tipo, Sugerencia... sugerencias) {
        gruposPorTipo.put(tipo, List.of(sugerencias));
    }

    private static Sugerencia grupo(String nombre, int minimo, int maximo, String... opciones) {
        return new Sugerencia(nombre, minimo, maximo, List.of(opciones));
    }

    private static Campo ingredientes(boolean obligatorio) {
        return listaDe("ingredientes", "Ingredientes", obligatorio);
    }

    private static Campo alergenos() {
        return listaDe("alergenos", "Alérgenos", false);
    }

    private static Campo vegetariano() {
        return booleanoDe("vegetariano", "Vegetariano");
    }

    private static Campo vegano() {
        return booleanoDe("vegano", "Vegano");
    }

    private static Campo sinGluten() {
        return booleanoDe("sin_gluten", "Sin gluten");
    }

    private static Campo picante() {
        return booleanoDe("picante", "Picante");
    }

    private static Campo listaDe(String clave, String etiqueta, boolean obligatorio) {
        return new Campo(clave, etiqueta, TipoCampo.LISTA, obligatorio, List.of(), null, null);
    }

    private static Campo numeroDe(String clave, String etiqueta, boolean obligatorio, double min, double max) {
        return new Campo(clave, etiqueta, TipoCampo.NUMERO, obligatorio, List.of(), min, max);
    }

    private static Campo booleanoDe(String clave, String etiqueta) {
        return new Campo(clave, etiqueta, TipoCampo.BOOLEANO, false, List.of(), null, null);
    }

    private static ResponseStatusException invalida(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}