package org.example.producer;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "producer")
public class ProducerProperties {
    private String backendUrl = "http://localhost:8081/api/logs";
    private int tps = 1000;
    private int batchSize = 100;
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 5000;
}