# High-Throughput Log Ingestion System

Hệ thống Java xử lý hàng chục triệu log/ngày: **producer → nginx → queue → batch → dual-sink (PostgreSQL + Elasticsearch) → ELK**.

---

## Kiến trúc
<img width="646" height="963" alt="image" src="https://github.com/user-attachments/assets/5cfd45b4-0f8b-44aa-8aee-bb43b3f75b2f" />


```
Producer (1.000 log/s)
    │  HTTP POST /api/logs · batch 100 logs
    ▼
Nginx (Load Balancer · Rate Limit 600 r/s · Burst 200)
    │  round-robin · 429 nếu vượt rate
    ▼
LogController × 3 replicas
    │  413 nếu batch > 5.000 · 503 nếu queue đầy · 202 nếu ok
    ▼
LogQueue (LinkedBlockingQueue · 500.000 slots · non-blocking offer)
    │  drainTo 500 logs/lần
    ▼
LogBatchConsumer (4 threads)
    ├──► PostgresLogSink  — JDBC batch INSERT  (retry ≤3 · backoff 100→200→400ms)
    └──► ElasticsearchSink — bulk index         (retry ≤3 · backoff 100→200→400ms)
    │
    ▼
BackendStatsReporter (1s)
    ├──► stdout ASCII dashboard
    └──► Filebeat → Logstash → Elasticsearch → Kibana
```

---

## Yêu cầu 1 — Client sinh dữ liệu

`ProducerScheduler` tick **mỗi 100ms**, sinh `TPS/10` log rồi POST lên backend:

```
tps=1000 → 100 log/100ms → 1.000 log/s
```

Dữ liệu sinh ngẫu nhiên: `timestamp`, `ip`, `method` (GET/POST/PUT/DELETE), `path`, `status`.

**Cấu hình** (`application.properties`):

```properties
producer.tps=1000          # thay đổi TPS
producer.batch-size=100    # log mỗi request
producer.backend-url=http://localhost:8081/api/logs
```

Chạy với TPS khác: `java -jar producer.jar --producer.tps=5000`

---

## Yêu cầu 2 — Queue, Batch, Retry

**Queue:** Mọi log bắt buộc đi qua `LinkedBlockingQueue(500k)`. Dùng `offer()` (non-blocking) — queue đầy trả `false` → controller trả 503.

**Batch:** Consumer drain **500 log/lần**, ghi song song vào mọi sink qua `Future`. Một sink lỗi không block sink kia.

**Retry + Exponential Backoff:**

```
sink.insert(batch)
    ├── success → done
    └── fail
          ├── attempt > 3 → DROP
          └── sleep 100ms × 2^(attempt-1) → retry
                attempt 1 → 100ms
                attempt 2 → 200ms
                attempt 3 → 400ms
```

---

## Yêu cầu 3 — Thống kê

Dashboard cập nhật mỗi giây:

```
+----------------------------------------------------------+
| LOG BACKEND  elapsed=42s  JVM mem: 187MB / 512MB        |
+----------------------------------------------------------+
| Received  :    187,432 total  |   4,512 /s               |
| Dropped   :          0 total  |       0 /s               |
| [postgres    ] inserted=  183,000 (4,500/s) retries=0 failed=0 |
| [elastic     ] inserted=  183,000 (4,500/s) retries=2 failed=0 |
| Queue     :    1,250 / 500,000  [#---------] 0.3%       |
+----------------------------------------------------------+
```

Log JSON `type=metrics` gửi qua Filebeat → Logstash → Kibana để visualize theo thời gian thực.

---

## Yêu cầu 4 — Chống quá tải (4 lớp)

| Lớp | Cơ chế | Phản hồi |
|-----|--------|---------|
| Nginx edge | Rate limit 600 r/s per IP, burst 200 | 429 |
| Controller | Batch > 5.000 log | 413 |
| Queue | `offer()` = false khi đầy | 503 |
| Thread pool | Consumer tách biệt HTTP thread | DB lỗi không ảnh hưởng ingest |

---

## Yêu cầu 5 — Mở rộng

- **Stateless backend:** thêm replica = thêm 1 service trong `docker-compose.yml` + 1 dòng `nginx.conf`
- **Pluggable sink:** implement `LogSink` interface, Spring tự inject — không sửa code cũ
- **Cấu hình động:** tất cả tham số qua `application.properties` hoặc env var

---

## Chạy

```bash
# 1. Build backend
cd log-backend && mvn clean package -DskipTests

# 2. Khởi động infrastructure
docker-compose up -d

# 3. Chạy producer
cd log-producer-client && mvn clean package -DskipTests
java -jar target/log-producer-client-*.jar

# 4. Xem log
docker logs -f log-backend-1

# 5. Kibana dashboard
open http://localhost:5601
```

---

## Giám sát RAM / CPU
<img width="261" height="157" alt="image" src="https://github.com/user-attachments/assets/f60de744-8a3d-4bc0-95b8-89a5d502c287" />
<img width="255" height="159" alt="image" src="https://github.com/user-attachments/assets/71aa2905-60e0-47cf-a925-77ec0832fb04" />
<img width="252" height="152" alt="image" src="https://github.com/user-attachments/assets/f64975f0-a682-42ab-818f-411fdee431fa" />

```

Chụp màn hình **3 thời điểm**: trước khi chạy (baseline) → trong khi chạy (peak) → sau khi dừng (release).
