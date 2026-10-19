package com.nexo.msqueues.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
public class AwsSqsConfig {
    @Bean
    public SqsClient sqsClient(SqsProperties props) {
        return SqsClient.builder()
                .region(Region.of(props.region()))
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }
}