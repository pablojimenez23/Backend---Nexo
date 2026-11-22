package com.nexo.msqueues.config;

import com.nexo.msqueues.service.KinesisAdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, deja en AWS los streams que declara application.yml (nexo.kinesis.streams).
 * Igual que QueueBootstrap, es idempotente y un error no impide que el servicio arranque.
 */
@Component
public class KinesisBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KinesisBootstrap.class);

    private final KinesisAdminService kinesisAdmin;
    private final String streams;
    private final int shards;

    public KinesisBootstrap(KinesisAdminService kinesisAdmin,
                            @Value("${nexo.kinesis.streams:nexo-pedidos-stream}") String streams,
                            @Value("${nexo.kinesis.shards:1}") int shards) {
        this.kinesisAdmin = kinesisAdmin;
        this.streams = streams;
        this.shards = shards;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String nombre : streams.split(",")) {
            String limpio = nombre.trim();
            if (limpio.isEmpty()) {
                continue;
            }
            try {
                boolean creado = kinesisAdmin.asegurarStream(limpio, shards);
                log.info(creado ? "Stream '{}' creado con {} shard(s)" : "Stream '{}' ya existía, se mantiene",
                        limpio, shards);
            } catch (Exception e) {
                log.error("No se pudo preparar el stream '{}': {}", limpio, e.getMessage());
            }
        }
    }
}