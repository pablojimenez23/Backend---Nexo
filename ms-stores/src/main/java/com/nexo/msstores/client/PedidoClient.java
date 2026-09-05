package com.nexo.msstores.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Component
public class PedidoClient {

    private final RestClient restClient = RestClient.create("http://localhost:8084");

    @Value("${internal.api.key}")
    private String internalApiKey;

    public boolean pedidoCompletadoValido(UUID pedidoId, UUID clienteId, UUID tiendaId) {
        try {
            Map<?, ?> respuesta = restClient.get()
                    .uri("/pedidos/{id}/verificar-completado?clienteId={clienteId}&tiendaId={tiendaId}",
                            pedidoId, clienteId, tiendaId)
                    .header("X-Internal-Key", internalApiKey)
                    .retrieve()
                    .body(Map.class);
            return respuesta != null && Boolean.TRUE.equals(respuesta.get("valido"));
        } catch (Exception e) {
            return false;
        }
    }
}