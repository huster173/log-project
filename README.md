# High-Throughput Log Ingestion System

Hệ thống Java xử lý hàng chục triệu log mỗi ngày.

**Luồng xử lý:** Producer → Nginx → Queue → Batch → Dual-sink (PostgreSQL + Elasticsearch) → ELK

---

## 1. Tổng quan kiến trúc

<img width="646" height="963" alt="image" src="https://github.com/user-attachments/assets/5cfd45b4-0f8b-44aa-8aee-bb43b3f75b2f" />

```
Producer (1.000 log/s)
    │  HTTP POST /api/logs · batch 100 logs
    ▼
Nginx — Load Balancer
    │  Rate limit 600 r/s · Burst 200
    │  round-robin · trả 429 nếu vượt rate
    ▼
LogController × 3 replicas
    │  413 nếu batch > 5.000
    │  503 nếu queue đầy
    │  202 nếu OK
    ▼
LogQueue
    │  LinkedBlockingQueue · 500.000 slots
    │  non-blocking offer()
    │  drainTo 500 logs/lần
    ▼
LogBatchConsumer (4 threads)
    ├──► PostgresLogSink       — JDBC batch INSERT   (retry ≤3, backoff 100→200→400ms)
    └──► ElasticsearchSink     — bulk index           (retry ≤3, backoff 100→200→400ms)
    │
    ▼
BackendStatsReporter (mỗi 1s)
    ├──► stdout ASCII dashboard
    └──► Filebeat → Logstash → Elasticsearch → Kibana
```

---

## 2. Sinh dữ liệu (Producer)

`ProducerScheduler` chạy tick mỗi **100ms**, mỗi tick sinh `TPS / 10` log rồi gửi POST lên backend.

```
tps = 1000  →  100 log / 100ms  →  1.000 log/s
```

Mỗi log gồm các trường sinh ngẫu nhiên: `timestamp`, `ip`, `method` (GET/POST/PUT/DELETE), `path`, `status`.

**Cấu hình** trong `application.properties`:

```properties
producer.tps=1000          # số log/giây cần sinh
producer.batch-size=100    # số log gửi mỗi request
producer.backend-url=http://localhost:8081/api/logs
```

Chạy với TPS tùy chỉnh:

```bash
java -jar producer.jar --producer.tps=5000
```

---

## 3. Queue, Batch, Retry

**Queue**
Mọi log đều phải đi qua `LinkedBlockingQueue(500.000)`.
Dùng `offer()` (non-blocking) — nếu queue đầy, `offer()` trả `false` và controller trả về **503**.

**Batch**
Consumer gom **500 log/lần**, ghi song song vào tất cả sink thông qua `Future`. Nếu một sink lỗi, sink còn lại **không bị ảnh hưởng**.

**Retry với Exponential Backoff**

```
sink.insert(batch)
    ├── thành công → xong
    └── thất bại
          ├── đã thử > 3 lần → DROP batch
          └── chờ 100ms × 2^(lần thử - 1) rồi thử lại

              Lần 1 → chờ 100ms
              Lần 2 → chờ 200ms
              Lần 3 → chờ 400ms
```

---

## 4. Thống kê real-time

Dashboard cập nhật mỗi giây trên console:

```
+----------------------------------------------------------+
| LOG BACKEND  elapsed=42s  JVM mem: 187MB / 512MB          |
+----------------------------------------------------------+
| Received  :    187,432 total  |   4,512 /s                |
| Dropped   :          0 total  |       0 /s                |
| [postgres    ] inserted=183,000 (4,500/s) retries=0 failed=0  |
| [elastic     ] inserted=183,000 (4,500/s) retries=2 failed=0  |
| Queue     :    1,250 / 500,000  [#---------] 0.3%          |
+----------------------------------------------------------+
```

Các log dạng JSON (`type=metrics`) cũng được gửi qua **Filebeat → Logstash → Kibana** để visualize theo thời gian thực.

---

## 5. Chống quá tải — 4 lớp bảo vệ

| Lớp | Cơ chế | Phản hồi khi quá tải |
|---|---|---|
| **Nginx edge** | Rate limit 600 request/s mỗi IP, burst 200 | `429` |
| **Controller** | Từ chối batch > 5.000 log | `413` |
| **Queue** | `offer()` trả `false` khi đầy | `503` |
| **Thread pool** | Consumer chạy tách biệt khỏi HTTP thread | Lỗi DB không ảnh hưởng luồng nhận log |

---

## 6. Khả năng mở rộng

- **Stateless backend** — thêm replica chỉ cần thêm 1 service trong `docker-compose.yml` + 1 dòng cấu hình trong `nginx.conf`
- **Pluggable sink** — implement interface `LogSink`, Spring sẽ tự inject, không cần sửa code cũ
- **Cấu hình động** — mọi tham số đều điều chỉnh qua `application.properties` hoặc biến môi trường

---

## 7. Hướng dẫn chạy

```bash
# 1. Build backend
cd log-backend && mvn clean package -DskipTests

# 2. Khởi động infrastructure (Postgres, Elasticsearch, ...)
docker-compose up -d

# 3. Chạy producer
cd log-producer-client && mvn clean package -DskipTests
java -jar target/log-producer-client-*.jar

# 4. Theo dõi log backend
docker logs -f log-backend-1

# 5. Mở Kibana dashboard
open http://localhost:5601
```

---

## 8. Giám sát RAM / CPU

<img width="261" height="157" alt="image" src="https://github.com/user-attachments/assets/f60de744-8a3d-4bc0-95b8-89a5d502c287" />
<img width="255" height="159" alt="image" src="https://github.com/user-attachments/assets/71aa2905-60e0-47cf-a925-77ec0832fb04" />
<img width="252" height="152" alt="image" src="https://github.com/user-attachments/assets/f64975f0-a682-42ab-818f-411fdee431fa" />



> **Lưu ý:** Chụp màn hình ở **3 thời điểm** để so sánh:
> 1. trước khi chạy
> 2. trong khi hệ thống đang chạy 
> 3. sau khi dừng hệ thống
