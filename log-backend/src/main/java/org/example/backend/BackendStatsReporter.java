package org.example.backend;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Prints backend stats dashboard to stdout every second and emits structured metrics log for ELK.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BackendStatsReporter {

    private final BackendStats stats;
    private final LogQueue     queue;
    private final List<LogSink> sinks;

    private final long startTime = System.currentTimeMillis();

    @PostConstruct
    void start() {
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "stats-reporter");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(this::report, 1, 1, TimeUnit.SECONDS);
    }

    private void report() {
        long elapsedSec   = (System.currentTimeMillis() - startTime) / 1000;
        long receivedRate = stats.rateReceived();
        long droppedRate  = stats.rateDropped();

        double fillPct = queue.fillRatio() * 100;
        String bar     = fillBar(queue.fillRatio());

        Runtime rt   = Runtime.getRuntime();
        long usedMB  = (rt.totalMemory() - rt.freeMemory()) / 1_048_576;
        long maxMB   = rt.maxMemory() / 1_048_576;

        String dropAlert = stats.getTotalDropped() > 0 ? " [!!!]" : "";

        // rateInserted() has a side effect (resets the snapshot), so compute it once per sink and reuse.
        Map<String, Long> insertedRateBySink = new LinkedHashMap<>();
        for (LogSink sink : sinks) {
            insertedRateBySink.put(sink.name(), stats.rateInserted(sink.name()));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
            "%n+----------------------------------------------------------+%n" +
            "| LOG BACKEND  elapsed=%-5ds  JVM mem: %dMB / %dMB        |%n" +
            "+----------------------------------------------------------+%n" +
            "| Received  : %,10d total  | %,7d /s               |%n" +
            "| Dropped   : %,10d total  | %,7d /s%-6s          |%n",
            elapsedSec, usedMB, maxMB,
            stats.getTotalReceived(), receivedRate,
            stats.getTotalDropped(),  droppedRate, dropAlert
        ));
        for (LogSink sink : sinks) {
            sb.append(String.format(
                "| [%-12s] inserted=%,9d (%,6d/s) retries=%,6d failed=%,6d |%n",
                sink.name(), stats.getInserted(sink.name()), insertedRateBySink.get(sink.name()),
                stats.getRetries(sink.name()), stats.getFailed(sink.name())
            ));
        }
        sb.append(String.format(
            "| Queue     : %,7d / %,d  %s %.1f%%      |%n" +
            "+----------------------------------------------------------+%n",
            queue.size(), queue.capacity(), bar, fillPct
        ));
        System.out.print(sb);

        // Structured metrics log → picked up by Filebeat → Kibana
        StringBuilder sinkFields = new StringBuilder();
        for (LogSink sink : sinks) {
            sinkFields.append(String.format(" %s_inserted_tps=%d %s_total_inserted=%d %s_total_retries=%d %s_total_failed=%d",
                    sink.name(), insertedRateBySink.get(sink.name()),
                    sink.name(), stats.getInserted(sink.name()),
                    sink.name(), stats.getRetries(sink.name()),
                    sink.name(), stats.getFailed(sink.name())));
        }
        log.info("type=metrics received_tps={} dropped_tps={} " +
                 "total_received={} total_dropped={} " +
                 "queue_size={} queue_capacity={} queue_fill_pct={} jvm_used_mb={} jvm_max_mb={}{}",
                receivedRate, droppedRate,
                stats.getTotalReceived(),
                stats.getTotalDropped(),
                queue.size(), queue.capacity(), String.format("%.1f", fillPct),
                usedMB, maxMB, sinkFields);
    }

    private String fillBar(double ratio) {
        int filled = (int) Math.min(ratio * 10, 10);
        return "[" + "#".repeat(filled) + "-".repeat(10 - filled) + "]";
    }
}
