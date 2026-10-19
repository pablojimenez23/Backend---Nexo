package com.nexo.msqueues.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Topics SNS y reglas de distribución: qué eventos recibe cada cola.
 */
@ConfigurationProperties(prefix = "nexo.sns")
public record SnsProperties(List<TopicDef> topics) {

    public SnsProperties {
        topics = topics == null ? List.of() : topics;
    }

    public record TopicDef(String name, List<SubscriptionDef> subscriptions) {
        public TopicDef {
            subscriptions = subscriptions == null ? List.of() : subscriptions;
        }
    }

    public record SubscriptionDef(String queue, List<String> eventos) {
        public SubscriptionDef {
            eventos = eventos == null ? List.of() : eventos;
        }
    }
}