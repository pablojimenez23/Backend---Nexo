package com.nexo.msqueues.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

@Configuration
public class AwsSnsConfig {
    @Bean
    public SnsClient snsClient(SqsProperties props) {
        return SnsClient.builder()
                .region(Region.of(props.region()))
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }
}