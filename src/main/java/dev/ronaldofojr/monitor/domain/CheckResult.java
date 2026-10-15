package dev.ronaldofojr.monitor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Resultado de uma checagem (uma linha do historico). */
@Entity
@Table(name = "check_results")
public class CheckResult {

    @Id
    private UUID id;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    private String error;

    protected CheckResult() {
    }

    public CheckResult(UUID targetId, Instant checkedAt, boolean success, Integer httpStatus, long latencyMs, String error) {
        this.id = UUID.randomUUID();
        this.targetId = targetId;
        this.checkedAt = checkedAt;
        this.success = success;
        this.httpStatus = httpStatus;
        this.latencyMs = latencyMs;
        this.error = error == null || error.length() <= 300 ? error : error.substring(0, 300);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public boolean isSuccess() {
        return success;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public String getError() {
        return error;
    }
}
