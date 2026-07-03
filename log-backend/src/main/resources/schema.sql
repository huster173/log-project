CREATE TABLE IF NOT EXISTS log_entries (
    id     BIGSERIAL PRIMARY KEY,
    ts     TIMESTAMPTZ NOT NULL,
    ip     VARCHAR(45) NOT NULL,
    method VARCHAR(10) NOT NULL,
    path   TEXT NOT NULL,
    status INT NOT NULL
);
