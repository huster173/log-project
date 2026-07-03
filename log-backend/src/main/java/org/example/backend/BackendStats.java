package org.example.backend;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class BackendStats {

    private final AtomicLong totalReceived = new AtomicLong();
    private final AtomicLong totalDropped  = new AtomicLong();

    // Per-sink counters, keyed by LogSink.name() (e.g. "postgres", "elasticsearch").
    private final Map<String, AtomicLong> inserted = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> retries  = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> failed   = new ConcurrentHashMap<>();

    private final AtomicLong snapReceived = new AtomicLong();
    private final AtomicLong snapDropped  = new AtomicLong();
    private final Map<String, AtomicLong> snapInserted = new ConcurrentHashMap<>();

    public void addReceived(long n) { totalReceived.addAndGet(n); }
    public void addDropped(long n)  { totalDropped.addAndGet(n); }

    public void addInserted(String sink, long n) { counter(inserted, sink).addAndGet(n); }
    public void incrementRetries(String sink)    { counter(retries, sink).incrementAndGet(); }
    public void addFailed(String sink, long n)   { counter(failed, sink).addAndGet(n); }

    public long getTotalReceived() { return totalReceived.get(); }
    public long getTotalDropped()  { return totalDropped.get(); }

    public long getInserted(String sink) { return counter(inserted, sink).get(); }
    public long getRetries(String sink)  { return counter(retries, sink).get(); }
    public long getFailed(String sink)   { return counter(failed, sink).get(); }

    public long rateReceived() {
        long cur = totalReceived.get();
        return cur - snapReceived.getAndSet(cur);
    }

    public long rateDropped() {
        long cur = totalDropped.get();
        return cur - snapDropped.getAndSet(cur);
    }

    public long rateInserted(String sink) {
        long cur = getInserted(sink);
        return cur - counter(snapInserted, sink).getAndSet(cur);
    }

    private AtomicLong counter(Map<String, AtomicLong> map, String key) {
        return map.computeIfAbsent(key, k -> new AtomicLong());
    }
}
