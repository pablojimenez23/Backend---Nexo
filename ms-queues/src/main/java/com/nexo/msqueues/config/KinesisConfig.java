package com.nexo.msqueues.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisClient;

@Configuration
public class KinesisConfig {

    @Bean
    public KinesisClient kinesisClient(@Value("${nexo.kinesis.region:us-east-1}") String region) {
        return KinesisClient.builder()
                .httpClient(UrlConnectionHttpClient.create())
                .region(Region.of(region))
                .build();
    }
}