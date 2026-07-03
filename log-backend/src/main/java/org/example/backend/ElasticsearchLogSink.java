package org.example.backend;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Search/visualization sink: bulk-indexes the same batch into a daily
 * Elasticsearch index so raw business logs are queryable in Kibana directly,
 * without going through the Filebeat/Logstash app-log pipeline.
 */
@Component
@RequiredArgsConstructor
public class ElasticsearchLogSink implements LogSink {

    private static final DateTimeFormatter INDEX_DATE =
            DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneOffset.UTC);

    private final ElasticsearchClient esClient;

    @Override
    public String name() {
        return "elasticsearch";
    }

    @Override
    public void insert(List<LogRequest> batch) throws Exception {
        String index = "log-entries-" + INDEX_DATE.format(Instant.now());

        BulkRequest.Builder br = new BulkRequest.Builder();
        for (LogRequest r : batch) {
            br.operations(op -> op.index(idx -> idx.index(index).document(r)));
        }

        BulkResponse result = esClient.bulk(br.build());
        if (result.errors()) {
            StringBuilder reasons = new StringBuilder();
            for (BulkResponseItem item : result.items()) {
                if (item.error() != null) {
                    reasons.append(item.error().reason()).append("; ");
                }
            }
            throw new IllegalStateException("Elasticsearch bulk insert had errors: " + reasons);
        }
    }
}
