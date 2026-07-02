package org.example.producer;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class LogGenerator {

    private static final String[] METHODS  = {"GET", "POST", "PUT", "DELETE"};
    private static final String[] PATHS    = {"/login", "/users", "/orders", "/products", "/payment"};
    private static final int[]    STATUSES = {200, 201, 400, 401, 403, 404, 500};

    public LogRequest generate() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return LogRequest.builder()
                .timestamp(System.currentTimeMillis())
                .ip(r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(256))
                .method(METHODS[r.nextInt(METHODS.length)])
                .path(PATHS[r.nextInt(PATHS.length)])
                .status(STATUSES[r.nextInt(STATUSES.length)])
                .build();
    }

    public List<LogRequest> generateBatch(int size) {
        List<LogRequest> batch = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            batch.add(generate());
        }
        return batch;
    }
}
