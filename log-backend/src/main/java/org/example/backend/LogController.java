package org.example.backend;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoint that accepts batches of log entries from the producer client.
 * Enqueues each log into LogQueue for async batch-insert processing.
 */
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Slf4j
public class LogController {

    private final LogQueue logQueue;
    private final BackendStats stats;
    private final ConsumerProperties config;

    @PostMapping
    public ResponseEntity<LogIngestResponse> receiveLogs(@RequestBody List<LogRequest> batch, HttpServletRequest request) {
        long start = System.currentTimeMillis();

        // Reject oversized requests outright instead of burning CPU enqueuing them
        // one-by-one only to have most of them dropped by the queue anyway.
        if (batch.size() > config.getMaxBatchSize()) {
            logRequest(request, batch.size(), 0, batch.size(), HttpStatus.PAYLOAD_TOO_LARGE.value(), start);
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(new LogIngestResponse(0, batch.size()));
        }

        int dropped = 0;
        for (LogRequest log : batch) {
            if (!logQueue.offer(log)) {
                dropped++;
            }
        }

        stats.addReceived(batch.size());
        if (dropped > 0) {
            stats.addDropped(dropped);
        }

        LogIngestResponse body = new LogIngestResponse(batch.size() - dropped, dropped);

        // Signal backpressure instead of always claiming success — callers should
        // treat a non-2xx here as "slow down / retry later", not a fire-and-forget ack.
        HttpStatus status = dropped > 0 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.ACCEPTED;
        logRequest(request, batch.size(), batch.size() - dropped, dropped, status.value(), start);
        return ResponseEntity.status(status).body(body);
    }

    // Emits a structured "type=request" line per call → picked up by Filebeat → Kibana,
    // so request volume/latency can be broken down per instance behind the LB.
    private void logRequest(HttpServletRequest request, int batchSize, int accepted, int dropped, int status, long startMillis) {
        String remoteAddr = request.getHeader("X-Real-IP") != null
                ? request.getHeader("X-Real-IP")
                : request.getRemoteAddr();
        log.info("type=request method={} path={} remote_addr={} batch_size={} accepted={} dropped={} status={} duration_ms={}",
                request.getMethod(), request.getRequestURI(), remoteAddr,
                batchSize, accepted, dropped, status, System.currentTimeMillis() - startMillis);
    }
}
