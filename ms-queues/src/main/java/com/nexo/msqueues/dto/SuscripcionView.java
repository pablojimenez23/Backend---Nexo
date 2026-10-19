package com.nexo.msqueues.dto;

import java.util.List;

public record SuscripcionView(String queue, String queueArn, List<String> eventos, String suscripcionArn) {
}