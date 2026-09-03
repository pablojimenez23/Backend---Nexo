package com.nexo.msstores.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Component
public class NotificacionClient {

    private final RestClient restClient = RestClient.create("http://localhost:8081");

    @Value("${internal.api.key}")
    private String internalApiKey;

    public void enviar(UUID usuarioId, String titulo, String mensaje) {
        try {
            restClient.post()
                    .uri("/notificaciones/internal")
                    .header("X-Internal-Key", internalApiKey)
                    .body(Map.of("usuarioId", usuarioId, "titulo", titulo, "mensaje", mensaje))
                    .retrieve()
                    .toBodilessEntity();
            System.out.println("Notificación enviada OK a usuario " + usuarioId);
        } catch (Exception e) {
            System.out.println("ERROR al enviar notificación: " + e.getMessage());
        }
    }
}