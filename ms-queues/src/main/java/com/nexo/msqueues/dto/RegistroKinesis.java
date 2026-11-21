package com.nexo.msqueues.dto;

import java.time.Instant;

/** Un registro leído de un stream de Kinesis, para mostrarlo en la administración. */
public record RegistroKinesis(
        String shardId,
        String numeroSecuencia,
        Instant llegadaEn,
        String claveParticion,
        String datos) {
}