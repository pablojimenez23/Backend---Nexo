package com.nexo.msqueues.dto;

import java.util.Map;

public record MensajeView(
        String messageId,
        String body,
        int receiveCount,
        Map<String, String> atributos) {
}