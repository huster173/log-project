package org.example.backend;

import java.util.List;

/**
 * A destination that a drained batch of logs gets written to.
 * LogBatchConsumer writes the same batch to every LogSink bean in parallel,
 * independently retrying/backing off per sink.
 */
public interface LogSink {
    String name();
    void insert(List<LogRequest> batch) throws Exception;
}
