package com.nexo.msqueues.dto;

public record QueueResponse(
        String name, String url, String arn,
        long mensajesDisponibles, long mensajesEnProceso,
        int visibilityTimeoutSeconds, int retentionSeconds,
        String dlqArn, Integer maxReceiveCount) {
}