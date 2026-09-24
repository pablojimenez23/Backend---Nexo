package com.nexo.msorders.client;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class ProductoClient {

    private final RestClient restClient = RestClient.create("http://nexo-alb-326907716.us-east-1.elb.amazonaws.com/products");

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

    public void reservarStock(UUID productoId, int cantidad) {
        try {
            restClient.patch()
                    .uri("/productos/{id}/reservar-stock?cantidad={cantidad}", productoId, cantidad)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            System.out.println("DEBUG ERROR reservarStock: " + e.getClass().getName() + " - " + e.getMessage());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuficiente para el producto");
        }
    }

    public void revertirStock(UUID productoId, int cantidad) {
        try {
            restClient.patch()
                    .uri("/productos/{id}/revertir-stock?cantidad={cantidad}", productoId, cantidad)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // No frenamos la cancelación por esto
        }
    }

    public record ProductoDTO(UUID id, UUID tiendaId, String nombre, BigDecimal precio, Integer stock, Boolean disponible) {
    }
}