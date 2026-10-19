package com.nexo.msorders.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexo.mensajeria")
public record MensajeriaProperties(String region, String topicPedidos) {
}