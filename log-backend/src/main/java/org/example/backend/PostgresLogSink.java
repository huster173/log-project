package org.example.backend;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;

/**
 * System-of-record sink: batch-inserts the same batch into Postgres.
 */
@Component
@RequiredArgsConstructor
public class PostgresLogSink implements LogSink {

    private static final String INSERT_SQL =
            "INSERT INTO log_entries (ts, ip, method, path, status) VALUES (?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public String name() {
        return "postgres";
    }

    @Override
    public void insert(List<LogRequest> batch) {
        jdbcTemplate.batchUpdate(INSERT_SQL, batch, batch.size(), (ps, r) -> {
            ps.setTimestamp(1, new Timestamp(r.getTimestamp()));
            ps.setString(2, r.getIp());
            ps.setString(3, r.getMethod());
            ps.setString(4, r.getPath());
            ps.setInt(5, r.getStatus());
        });
    }
}
