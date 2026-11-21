package com.nexo.msorders.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisClient;

/** Cliente de Kinesis para publicar el stream de eventos de pedido. Usa las credenciales por defecto (LabRole). */
@Configuration
public class KinesisConfig {

    @Bean
    public KinesisClient kinesisClient(@Value("${nexo.mensajeria.region:us-east-1}") String region) {
        return KinesisClient.builder().region(Region.of(region)).build();
    }
}