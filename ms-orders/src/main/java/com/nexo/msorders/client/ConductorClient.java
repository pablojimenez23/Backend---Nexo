package com.nexo.msorders.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class ConductorClient {

    private final RestClient restClient = RestClient.create("http://nexo-alb-326907716.us-east-1.elb.amazonaws.com");

    @Value("${internal.api.key}")
    private String internalApiKey;

    public List<String> obtenerConductoresDisponibles() {
        try {
            String[] respuesta = restClient.get()
                    .uri("/conductores/internal/disponibles")
                    .header("X-Internal-Key", internalApiKey)
                    .retrieve()
                    .body(String[].class);
            return respuesta != null ? List.of(respuesta) : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }
}