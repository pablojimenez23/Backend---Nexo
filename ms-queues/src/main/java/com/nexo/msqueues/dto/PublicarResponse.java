package com.nexo.msqueues.dto;

import java.util.List;

/**
 * Resultado de publicar un evento de prueba: id del mensaje y colas que lo recibirán
 * según las reglas de distribución vigentes.
 */
public record PublicarResponse(String messageId, String topic, String evento, List<String> colasDestino) {
}