package org.example.producer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LogProducerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogProducerApplication.class, args);
    }
}