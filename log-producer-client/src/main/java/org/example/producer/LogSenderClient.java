package org.example.producer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

/**
 * Sends batches of log entries to the backend via HTTP POST.
 */
@Component
@Slf4j
public class LogSenderClient {

    private final RestTemplate restTemplate;
    private final ProducerProperties config;

    public LogSenderClient(ProducerProperties config) {
        this.config = config;
        this.restTemplate = new RestTemplate();
    }

    /**
     * POST a batch of logs to the backend.
     * Throws RuntimeException on failure so the caller can count failures.
     */
    public void sendBatch(List<LogRequest> batch) {
        restTemplate.postForEntity(
                URI.create(config.getBackendUrl()),
                batch,
                Void.class
        );
    }
}
