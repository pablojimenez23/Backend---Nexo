package com.nexo.msqueues.dto;

import java.util.List;

public record TopicResponse(String name, String arn, List<SuscripcionView> suscripciones) {
}