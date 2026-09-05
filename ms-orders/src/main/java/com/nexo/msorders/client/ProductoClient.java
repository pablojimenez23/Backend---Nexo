package com.nexo.msorders.client;

import com.nexo.msorders.common.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class ProductoClient {

    private final RestClient restClient = RestClient.create("http://localhost:8083");

    @Value("${internal.api.key}")
    private String internalApiKey;

    private final CircuitBreaker circuitBreaker = new CircuitBreaker(5, 10_000);

    public ProductoDTO obtenerProducto(UUID productoId) {
        if (!circuitBreaker.permiteLlamada()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El servicio de productos no está disponible en este momento. Intentá de nuevo en unos segundos.");
        }

        try {
            ProductoDTO producto = restClient.get()
                    .uri("/productos/{id}", productoId)
                    .retrieve()
                    .body(ProductoDTO.class);

            circuitBreaker.registrarExito();

            if (producto == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado");
            }
            return producto;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            circuitBreaker.registrarFallo();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo consultar el producto");
        }
    }

    public void reservarStock(UUID productoId, int cantidad) {
        if (!circuitBreaker.permiteLlamada()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El servicio de productos no está disponible en este momento. Intentá de nuevo en unos segundos.");
        }

        try {
            restClient.patch()
                    .uri("/productos/{id}/reservar-stock?cantidad={cantidad}", productoId, cantidad)
                    .header("X-Internal-Key", internalApiKey)
                    .retrieve()
                    .toBodilessEntity();
            circuitBreaker.registrarExito();
        } catch (Exception e) {
            circuitBreaker.registrarFallo();
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuficiente para el producto");
        }
    }

    public void revertirStock(UUID productoId, int cantidad) {
        try {
            restClient.patch()
                    .uri("/productos/{id}/revertir-stock?cantidad={cantidad}", productoId, cantidad)
                    .header("X-Internal-Key", internalApiKey)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // No frenamos la cancelación por esto
        }
    }

    public record ProductoDTO(UUID id, UUID tiendaId, String nombre, BigDecimal precio, Integer stock, Boolean disponible) {
    }
}