package com.nexo.msstores.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
public class ProductoClient {

    private final RestClient restClient = RestClient.create("http://localhost:8083");

    public boolean tieneProductos(UUID tiendaId) {
        try {
            List<?> productos = restClient.get()
                    .uri("/productos?tiendaId={id}", tiendaId)
                    .retrieve()
                    .body(List.class);
            return productos != null && !productos.isEmpty();
        } catch (Exception e) {
            // Si ms-products no responde, por seguridad asumimos que sí podría tener productos
            return true;
        }
    }
}