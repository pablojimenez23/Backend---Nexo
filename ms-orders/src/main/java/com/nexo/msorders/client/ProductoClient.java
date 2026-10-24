package com.nexo.msorders.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ProductoClient {

    private static final ReservaDTO SIN_OFERTA = new ReservaDTO(null, null, 0);

    private final RestClient restClient = RestClient.create(System.getenv().getOrDefault("ALB_URL", "http://nexo-alb-306620137.us-east-1.elb.amazonaws.com") + "/products");

    @Value("${internal.api.key}")
    private String internalApiKey;

    public ProductoDTO obtenerProducto(UUID productoId) {
        try {
            ProductoDTO producto = restClient.get()
                    .uri("/productos/{id}", productoId)
                    .retrieve()
                    .body(ProductoDTO.class);
            if (producto == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado");
            }
            return producto;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo consultar el producto");
        }
    }

    /**
     * Reserva stock (de la variante, si se manda varianteId) y consume el cupo de la oferta vigente.
     * La respuesta dice cuántas unidades se cobran a precio de oferta y a qué precio.
     */
    public ReservaDTO reservarStock(UUID productoId, UUID varianteId, int cantidad) {
        try {
            String uri = "/productos/{id}/reservar-stock?cantidad={cantidad}"
                    + (varianteId != null ? "&varianteId={varianteId}" : "");
            Object[] variables = varianteId != null
                    ? new Object[]{productoId, cantidad, varianteId}
                    : new Object[]{productoId, cantidad};

            ReservaDTO reserva = restClient.patch()
                    .uri(uri, variables)
                    .header("X-Internal-Key", internalApiKey)
                    .retrieve()
                    .body(ReservaDTO.class);
            return reserva == null ? SIN_OFERTA : reserva;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 400) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La opción elegida no es válida para el producto");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuficiente para el producto");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuficiente para el producto");
        }
    }

    /** Devuelve el stock (y el cupo de la oferta, si se usó). No frena la operación si falla. */
    public void revertirStock(UUID productoId, UUID varianteId, int cantidad, UUID ofertaId, int unidadesOferta) {
        try {
            String uri = "/productos/{id}/revertir-stock?cantidad={cantidad}";
            List<Object> variables = new ArrayList<>();
            variables.add(productoId);
            variables.add(cantidad);

            if (varianteId != null) {
                uri += "&varianteId={varianteId}";
                variables.add(varianteId);
            }
            if (ofertaId != null && unidadesOferta > 0) {
                uri += "&ofertaId={ofertaId}&unidadesOferta={unidadesOferta}";
                variables.add(ofertaId);
                variables.add(unidadesOferta);
            }

            restClient.patch()
                    .uri(uri, variables.toArray())
                    .header("X-Internal-Key", internalApiKey)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // No frenamos la cancelación por esto
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductoDTO(
            UUID id,
            UUID tiendaId,
            String nombre,
            BigDecimal precio,
            Integer stock,
            Boolean disponible,
            String tipo,
            Boolean requiereMayorEdad,
            List<VarianteDTO> variantes,
            List<GrupoDTO> grupos
    ) {
        /** Producto simple, sin variantes ni grupos de opciones. */
        public ProductoDTO(UUID id, UUID tiendaId, String nombre, BigDecimal precio, Integer stock, Boolean disponible) {
            this(id, tiendaId, nombre, precio, stock, disponible, null, false, List.of(), List.of());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VarianteDTO(UUID id, String nombre, BigDecimal precio, Integer stock, Boolean disponible) {
    }

    /** Grupo de opciones (masa, extras...): minimo >= 1 significa obligatorio. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GrupoDTO(UUID id, String nombre, Integer minimo, Integer maximo, List<OpcionDTO> opciones) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OpcionDTO(UUID id, String nombre, BigDecimal precioAdicional, Boolean disponible) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReservaDTO(UUID ofertaId, BigDecimal precioOferta, Integer unidadesEnOferta) {
    }
}