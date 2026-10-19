package com.nexo.msproducts.messaging;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lector JSON mínimo. Devuelve Map, List, String, BigDecimal, Boolean o null.
 * Cualquier problema se informa como MensajeInvalidoException (error definitivo).
 */
final class MiniJson {

    private final String s;
    private int i;

    private MiniJson(String s) {
        this.s = s;
    }

    static Object parse(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new MensajeInvalidoException("Mensaje vacío");
        }
        MiniJson p = new MiniJson(texto);
        try {
            p.espacios();
            Object v = p.valor();
            p.espacios();
            if (p.i != p.s.length()) {
                throw p.error("texto sobrante después del JSON");
            }
            return v;
        } catch (MensajeInvalidoException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new MensajeInvalidoException("JSON inválido: " + e.getMessage());
        }
    }

    private MensajeInvalidoException error(String detalle) {
        return new MensajeInvalidoException("JSON inválido en posición " + i + ": " + detalle);
    }

    private void espacios() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
    }

    private boolean ve(char c) {
        return i < s.length() && s.charAt(i) == c;
    }

    private void esperar(char c) {
        if (!ve(c)) {
            throw error("se esperaba '" + c + "'");
        }
        i++;
    }

    private Object valor() {
        if (i >= s.length()) {
            throw error("fin inesperado");
        }
        char c = s.charAt(i);
        switch (c) {
            case '{': return objeto();
            case '[': return arreglo();
            case '"': return cadena();
            case 't': literal("true"); return Boolean.TRUE;
            case 'f': literal("false"); return Boolean.FALSE;
            case 'n': literal("null"); return null;
            default: return numero();
        }
    }

    private Map<String, Object> objeto() {
        Map<String, Object> mapa = new LinkedHashMap<>();
        i++;
        espacios();
        if (ve('}')) {
            i++;
            return mapa;
        }
        while (true) {
            espacios();
            if (!ve('"')) {
                throw error("se esperaba una clave");
            }
            String clave = cadena();
            espacios();
            esperar(':');
            espacios();
            mapa.put(clave, valor());
            espacios();
            if (ve(',')) {
                i++;
                continue;
            }
            esperar('}');
            return mapa;
        }
    }

    private List<Object> arreglo() {
        List<Object> lista = new ArrayList<>();
        i++;
        espacios();
        if (ve(']')) {
            i++;
            return lista;
        }
        while (true) {
            espacios();
            lista.add(valor());
            espacios();
            if (ve(',')) {
                i++;
                continue;
            }
            esperar(']');
            return lista;
        }
    }

    private String cadena() {
        i++; // comilla de apertura
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (i >= s.length()) {
                throw error("cadena sin cerrar");
            }
            char c = s.charAt(i++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char e = s.charAt(i++);
            switch (e) {
                case '"': sb.append('"'); break;
                case '\\': sb.append('\\'); break;
                case '/': sb.append('/'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                case 'u':
                    sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                    break;
                default:
                    throw error("escape inválido");
            }
        }
    }

    private BigDecimal numero() {
        int inicio = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
            i++;
        }
        if (inicio == i) {
            throw error("valor inesperado");
        }
        return new BigDecimal(s.substring(inicio, i));
    }

    private void literal(String texto) {
        if (!s.startsWith(texto, i)) {
            throw error("se esperaba " + texto);
        }
        i += texto.length();
    }
}