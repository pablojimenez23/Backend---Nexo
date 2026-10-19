package com.nexo.msusers.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexo.mensajeria")
public record MensajeriaProperties(
        String region,
        String colaNotificaciones,
        String colaPuntos,
        Boolean consumidores) {

    public boolean consumidoresActivos() {
        return consumidores == null || consumidores;
    }
}