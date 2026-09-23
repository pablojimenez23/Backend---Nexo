package com.nexo.msproducts.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@Component
public class TiendaClient {

    private final RestClient restClient;

    public TiendaClient() {
        this.restClient = RestClient.create("http://nexo-alb-326907716.us-east-1.elb.amazonaws.com/stores");
    }

    public UUID obtenerOwnerId(UUID tiendaId) {
        try {
            TiendaDTO tienda = restClient.get()
                    .uri("/tiendas/{id}", tiendaId)
                    .retrieve()
                    .body(TiendaDTO.class);
            return tienda != null ? tienda.ownerId() : null;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo verificar la tienda");
        }
    }

    private record TiendaDTO(UUID id, UUID ownerId) {
    }
}