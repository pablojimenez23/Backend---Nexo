package com.nexo.msqueues;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MsQueuesApplication {
    public static void main(String[] args) {
        SpringApplication.run(MsQueuesApplication.class, args);
    }
}