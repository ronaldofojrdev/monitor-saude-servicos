package dev.ronaldofojr.monitor.api;

import dev.ronaldofojr.monitor.domain.Alert;
import dev.ronaldofojr.monitor.domain.AlertType;
import dev.ronaldofojr.monitor.domain.CheckResult;
import dev.ronaldofojr.monitor.domain.HttpMethodType;
import dev.ronaldofojr.monitor.domain.Target;
import dev.ronaldofojr.monitor.domain.TargetStatus;
import dev.ronaldofojr.monitor.service.TargetService.StatsView;
import dev.ronaldofojr.monitor.service.TargetService.TargetSettings;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Requests e responses da API num lugar so. Campos opcionais usam wrapper (Integer) para receber um padrao. */
public final class Dtos {

    private Dtos() {
    }

    public record TargetRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 500) String url,
            HttpMethodType method,
            @Min(10) @Max(86_400) Integer intervalSeconds,
            @Min(100) @Max(60_000) Integer timeoutMs,
            @Min(100) @Max(599) Integer expectedStatus,
            @Size(max = 200) String expectedBodyContains,
            @Min(1) @Max(20) Integer failureThreshold,
            Boolean active) {

        public TargetSettings toSettings() {
            return new TargetSettings(name.trim(), url,
                    method == null ? HttpMethodType.GET : method,
                    intervalSeconds == null ? 60 : intervalSeconds,
                    timeoutMs == null ? 5000 : timeoutMs,
                    expectedStatus == null ? 200 : expectedStatus,
                    expectedBodyContains,
                    failureThreshold == null ? 3 : failureThreshold);
        }

        public boolean activeOrDefault() {
            return active == null || active;
        }
    }

    public record TargetResponse(UUID id, String name, String url, HttpMethodType method, int intervalSeconds,
                                 int timeoutMs, int expectedStatus, String expectedBodyContains, int failureThreshold,
                                 boolean active, TargetStatus status, int consecutiveFailures, Instant lastCheckedAt,
                                 Instant lastStatusChangeAt) {
        public static TargetResponse from(Target t) {
            return new TargetResponse(t.getId(), t.getName(), t.getUrl(), t.getHttpMethod(), t.getIntervalSeconds(),
                    t.getTimeoutMs(), t.getExpectedStatus(), t.getExpectedBodyContains(), t.getFailureThreshold(),
                    t.isActive(), t.getStatus(), t.getConsecutiveFailures(), t.getLastCheckedAt(),
                    t.getLastStatusChangeAt());
        }
    }

    public record CheckResultResponse(UUID id, Instant checkedAt, boolean success, Integer httpStatus,
                                      long latencyMs, String error) {
        public static CheckResultResponse from(CheckResult r) {
            return new CheckResultResponse(r.getId(), r.getCheckedAt(), r.isSuccess(), r.getHttpStatus(),
                    r.getLatencyMs(), r.getError());
        }
    }

    public record StatsResponse(int hours, long totalChecks, long successfulChecks, Double uptimePercent,
                                Double avgLatencyMs, Double p95LatencyMs) {
        public static StatsResponse from(StatsView s) {
            return new StatsResponse(s.hours(), s.totalChecks(), s.successfulChecks(), s.uptimePercent(),
                    s.avgLatencyMs(), s.p95LatencyMs());
        }
    }

    public record AlertResponse(UUID id, UUID targetId, AlertType type, String message, Instant createdAt,
                                Instant notifiedAt, int notifyAttempts, String lastNotifyError) {
        public static AlertResponse from(Alert a) {
            return new AlertResponse(a.getId(), a.getTargetId(), a.getType(), a.getMessage(), a.getCreatedAt(),
                    a.getNotifiedAt(), a.getNotifyAttempts(), a.getLastNotifyError());
        }
    }
}
