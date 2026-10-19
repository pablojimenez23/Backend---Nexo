package com.nexo.msorders.messaging;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

@Configuration
@EnableConfigurationProperties(MensajeriaProperties.class)
public class MensajeriaConfig {

    @Bean
    public SnsClient snsClient(MensajeriaProperties props) {
        return SnsClient.builder()
                .region(Region.of(props.region()))
                .build();
    }
}