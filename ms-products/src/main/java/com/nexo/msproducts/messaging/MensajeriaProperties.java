package com.nexo.msproducts.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexo.mensajeria")
public record MensajeriaProperties(
        String region,
        String colaStock,
        Boolean consumidores) {

    public boolean consumidoresActivos() {
        return consumidores == null || consumidores;
    }
}