package com.nexo.msorders.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/** Habla con ms-users (a través del ALB) para gastar y devolver puntos de fidelidad. */
@Component
public class PuntosClient {

    private static final Logger log = LoggerFactory.getLogger(PuntosClient.class);

    private final RestClient restClient = RestClient.create(
            System.getenv().getOrDefault("ALB_URL", "http://nexo-alb-306620137.us-east-1.elb.amazonaws.com"));

    @Value("${internal.api.key}")
    private String internalApiKey;

    /** Descuenta los puntos del saldo del cliente y devuelve el id del canje. */
    public UUID canjear(UUID usuarioId, long puntos) {
        try {
            Map<?, ?> respuesta = restClient.post()
                    .uri("/puntos/internal/canjear")
                    .header("X-Internal-Key", internalApiKey)
                    .body(Map.of("usuarioId", usuarioId, "puntos", puntos))
                    .retrieve()
                    .body(Map.class);
            if (respuesta == null || respuesta.get("canjeId") == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo canjear los puntos");
            }
            return UUID.fromString(respuesta.get("canjeId").toString());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No tenés puntos suficientes para pagar esos productos con puntos");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo canjear los puntos");
        }
    }

    /** Devuelve los puntos de un canje (pedido cancelado o que no llegó a crearse). Idempotente. */
    public void revertir(UUID canjeId) {
        try {
            restClient.post()
                    .uri("/puntos/internal/canjes/{id}/revertir", canjeId)
                    .header("X-Internal-Key", internalApiKey)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // No frenamos la cancelación; el canje queda en el log para corregirlo a mano.
            log.error("No se pudieron devolver los puntos del canje {}: {}", canjeId, e.getMessage());
        }
    }
}