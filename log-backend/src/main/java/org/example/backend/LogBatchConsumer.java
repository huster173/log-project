package org.example.backend;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Drains LogQueue in batches and writes each batch to every configured LogSink
 * (Postgres = system of record, Elasticsearch = search/visualization) in parallel,
 * each with its own exponential backoff retry so one sink's trouble doesn't stall the other.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LogBatchConsumer {

    private final ConsumerProperties config;
    private final LogQueue           logQueue;
    private final BackendStats       stats;
    private final List<LogSink>      sinks;

    private final AtomicInteger threadId = new AtomicInteger();
    private ExecutorService sinkExecutor;

    @PostConstruct
    void start() {
        ExecutorService pool = Executors.newFixedThreadPool(
                config.getConsumerThreads(),
                r -> {
                    Thread t = new Thread(r, "log-consumer-" + threadId.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
        );

        // Sized so every consumer thread can have all sinks writing concurrently at once.
        sinkExecutor = Executors.newFixedThreadPool(
                config.getConsumerThreads() * Math.max(sinks.size(), 1),
                r -> {
                    Thread t = new Thread(r, "log-sink-writer");
                    t.setDaemon(true);
                    return t;
                }
        );

        for (int i = 0; i < config.getConsumerThreads(); i++) {
            pool.submit(this::consumeLoop);
        }

        log.info("Started {} consumer threads, batch size = {}, sinks = {}",
                config.getConsumerThreads(), config.getBatchSize(), sinks.stream().map(LogSink::name).toList());
    }

    private void consumeLoop() {
        List<LogRequest> buffer = new ArrayList<>(config.getBatchSize());
        while (!Thread.currentThread().isInterrupted()) {
            try {
                buffer.clear();
                int drained = logQueue.drainTo(buffer, config.getBatchSize());

                if (drained > 0) {
                    writeToAllSinks(List.copyOf(buffer));
                } else {
                    Thread.sleep(5);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Unexpected error in consumer loop", e);
            }
        }
    }

    private void writeToAllSinks(List<LogRequest> batch) throws InterruptedException {
        List<Future<?>> futures = new ArrayList<>(sinks.size());
        for (LogSink sink : sinks) {
            futures.add(sinkExecutor.submit(() -> insertWithRetry(sink, batch)));
        }
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (ExecutionException e) {
                log.error("Sink write task failed unexpectedly", e.getCause());
            }
        }
    }


    private void insertWithRetry(LogSink sink, List<LogRequest> batch) {
        int attempt = 0;
        while (true) {
            try {
                sink.insert(batch);
                stats.addInserted(sink.name(), batch.size());
                return;
            } catch (Exception e) {
                attempt++;
                stats.incrementRetries(sink.name());

                if (attempt > config.getMaxRetries()) {
                    stats.addFailed(sink.name(), batch.size());
                    log.error("[{}] Batch of {} logs dropped after {} retries: {}",
                            sink.name(), batch.size(), config.getMaxRetries(), e.getMessage());
                    return;
                }

                long backoff = config.getInitialBackoffMs() * (1L << (attempt - 1));
                log.warn("[{}] Insert attempt {} failed ({}), retrying in {}ms",
                        sink.name(), attempt, e.getMessage(), backoff);
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
