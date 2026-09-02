package com.nexo.msorders.client;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class TiendaClient {

    private final RestClient restClient = RestClient.create("http://localhost:8082");

    public TiendaDTO obtenerTienda(UUID tiendaId) {
        try {
            TiendaDTO tienda = restClient.get()
                    .uri("/tiendas/{id}", tiendaId)
                    .retrieve()
                    .body(TiendaDTO.class);
            if (tienda == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tienda no encontrada");
            }
            return tienda;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo consultar la tienda");
        }
    }

    public record TiendaDTO(UUID id, UUID ownerId, String estado, BigDecimal montoMinimo, String horario) {
    }
}