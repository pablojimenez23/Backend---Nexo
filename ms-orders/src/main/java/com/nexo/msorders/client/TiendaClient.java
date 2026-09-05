package com.nexo.msorders.client;

import com.nexo.msorders.common.CircuitBreaker;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class TiendaClient {

    private final RestClient restClient = RestClient.create("http://localhost:8082");

    // Se abre tras 5 fallos seguidos, se reintenta después de 10 segundos
    private final CircuitBreaker circuitBreaker = new CircuitBreaker(5, 10_000);

    public TiendaDTO obtenerTienda(UUID tiendaId) {
        if (!circuitBreaker.permiteLlamada()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El servicio de tiendas no está disponible en este momento. Intentá de nuevo en unos segundos.");
        }

        try {
            TiendaDTO tienda = restClient.get()
                    .uri("/tiendas/{id}", tiendaId)
                    .retrieve()
                    .body(TiendaDTO.class);

            circuitBreaker.registrarExito();

            if (tienda == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tienda no encontrada");
            }
            return tienda;
        } catch (ResponseStatusException e) {
            // Un 404 "tienda no encontrada" no cuenta como fallo del circuito, es un error de negocio normal
            throw e;
        } catch (Exception e) {
            circuitBreaker.registrarFallo();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo consultar la tienda");
        }
    }

    public record TiendaDTO(UUID id, UUID ownerId, String estado, BigDecimal montoMinimo, String horario) {
    }
}