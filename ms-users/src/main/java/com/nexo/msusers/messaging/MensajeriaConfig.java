package com.nexo.msusers.messaging;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
@EnableConfigurationProperties(MensajeriaProperties.class)
public class MensajeriaConfig {

    @Bean
    public SqsClient sqsClient(MensajeriaProperties props) {
        return SqsClient.builder()
                .region(Region.of(props.region()))
                .build();
    }
}