CREATE TABLE targets (
    id                      UUID PRIMARY KEY,
    name                    VARCHAR(100) NOT NULL UNIQUE,
    url                     VARCHAR(500) NOT NULL,
    http_method             VARCHAR(4)   NOT NULL,
    interval_seconds        INT          NOT NULL CHECK (interval_seconds >= 10),
    timeout_ms              INT          NOT NULL CHECK (timeout_ms >= 100),
    expected_status         INT          NOT NULL,
    expected_body_contains  VARCHAR(200),
    failure_threshold       INT          NOT NULL CHECK (failure_threshold >= 1),
    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    status                  VARCHAR(10)  NOT NULL,
    consecutive_failures    INT          NOT NULL DEFAULT 0,
    last_checked_at         TIMESTAMPTZ,
    last_status_change_at   TIMESTAMPTZ,
    -- quando o alvo deve ser checado de novo; o scheduler "reserva" o alvo atualizando este campo
    next_check_at           TIMESTAMPTZ  NOT NULL,
    version                 BIGINT       NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_targets_due ON targets (active, next_check_at);

CREATE TABLE check_results (
    id            UUID PRIMARY KEY,
    target_id     UUID        NOT NULL REFERENCES targets (id) ON DELETE CASCADE,
    checked_at    TIMESTAMPTZ NOT NULL,
    success       BOOLEAN     NOT NULL,
    http_status   INT,
    latency_ms    BIGINT      NOT NULL,
    error         VARCHAR(300)
);

CREATE INDEX idx_results_target_time ON check_results (target_id, checked_at DESC);
CREATE INDEX idx_results_time ON check_results (checked_at);

CREATE TABLE alerts (
    id                UUID PRIMARY KEY,
    target_id         UUID         NOT NULL REFERENCES targets (id) ON DELETE CASCADE,
    type              VARCHAR(10)  NOT NULL,
    message           VARCHAR(500) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    notified_at       TIMESTAMPTZ,
    notify_attempts   INT          NOT NULL DEFAULT 0,
    last_notify_error VARCHAR(300)
);

CREATE INDEX idx_alerts_created ON alerts (created_at DESC);
CREATE INDEX idx_alerts_target ON alerts (target_id, created_at DESC);
CREATE INDEX idx_alerts_pending ON alerts (notified_at) WHERE notified_at IS NULL;
