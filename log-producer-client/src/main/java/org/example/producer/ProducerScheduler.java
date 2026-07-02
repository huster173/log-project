package org.example.producer;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Fires every 100ms, generating TPS/10 logs per interval and sending them
 * as a batch to the backend service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProducerScheduler {

    private final ProducerProperties config;
    private final LogGenerator       generator;
    private final LogSenderClient    sender;

    @PostConstruct
    void start() {
        int logsPerInterval = Math.max(1, config.getTps() / 10); // 100ms window

        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "log-producer");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(() -> {
            List<LogRequest> batch = generator.generateBatch(logsPerInterval);
            try {
                sender.sendBatch(batch);
            } catch (Exception e) {
                log.warn("Failed to send batch of {}: {}", batch.size(), e.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);

        log.info("Producer started — target {} TPS ({} logs per 100ms)", config.getTps(), logsPerInterval);
    }
}
