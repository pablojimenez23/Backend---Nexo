package com.nexo.msqueues.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexo.sqs")
public record SqsProperties(String region, Defaults defaults, List<QueueDef> queues) {
    public record Defaults(int maxReceiveCount, int visibilityTimeoutSeconds, int retentionSeconds) {}
    public record QueueDef(String name, String dlqName) {}
}