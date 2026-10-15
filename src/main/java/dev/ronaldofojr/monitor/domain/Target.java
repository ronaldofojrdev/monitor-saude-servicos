package dev.ronaldofojr.monitor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Um servico monitorado: o que checar, com que frequencia e quando considerar que caiu. */
@Entity
@Table(name = "targets")
public class Target {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "http_method", nullable = false)
    private HttpMethodType httpMethod;

    @Column(name = "interval_seconds", nullable = false)
    private int intervalSeconds;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs;

    @Column(name = "expected_status", nullable = false)
    private int expectedStatus;

    @Column(name = "expected_body_contains")
    private String expectedBodyContains;

    @Column(name = "failure_threshold", nullable = false)
    private int failureThreshold;

    @Column(nullable = false)
    private boolean active;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TargetStatus status;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "last_status_change_at")
    private Instant lastStatusChangeAt;

    // updatable = false: este campo so muda pela query de "reserva" do repositorio (claimDue).
    // Se o JPA o regravasse num save() de fim de checagem, desfaria a reserva feita por outra instancia.
    @Column(name = "next_check_at", nullable = false, updatable = false)
    private Instant nextCheckAt;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Target() {
    }

    public Target(String name, String url, HttpMethodType httpMethod, int intervalSeconds, int timeoutMs,
                  int expectedStatus, String expectedBodyContains, int failureThreshold, Instant now) {
        this.id = UUID.randomUUID();
        this.status = TargetStatus.UNKNOWN;
        this.active = true;
        this.createdAt = now;
        this.nextCheckAt = now; // checa assim que o scheduler passar
        updateSettings(name, url, httpMethod, intervalSeconds, timeoutMs, expectedStatus, expectedBodyContains,
                failureThreshold, true);
    }

    public void updateSettings(String name, String url, HttpMethodType httpMethod, int intervalSeconds, int timeoutMs,
                               int expectedStatus, String expectedBodyContains, int failureThreshold, boolean active) {
        this.name = name;
        this.url = url;
        this.httpMethod = httpMethod;
        this.intervalSeconds = intervalSeconds;
        this.timeoutMs = timeoutMs;
        this.expectedStatus = expectedStatus;
        this.expectedBodyContains = expectedBodyContains == null || expectedBodyContains.isBlank() ? null : expectedBodyContains;
        this.failureThreshold = failureThreshold;
        this.active = active;
    }

    /** Pausado, o scheduler nao checa mais o alvo. O historico, o estado e os alertas continuam como estavam. */
    public void pause() {
        this.active = false;
    }

    public void resume() {
        this.active = true;
    }

    /**
     * Registra o resultado de uma checagem e diz se o estado do alvo mudou.
     *
     * Regras: o alvo so vira DOWN depois de failureThreshold falhas SEGUIDAS (uma oscilacao isolada nao alerta)
     * e volta a UP na primeira checagem bem-sucedida. O alerta so existe quando o estado muda,
     * entao um alvo que continua fora do ar nao gera alerta a cada checagem.
     */
    public Optional<AlertType> recordOutcome(boolean success, Instant now) {
        lastCheckedAt = now;
        if (success) {
            consecutiveFailures = 0;
            if (status == TargetStatus.DOWN) {
                status = TargetStatus.UP;
                lastStatusChangeAt = now;
                return Optional.of(AlertType.RECOVERED);
            }
            if (status == TargetStatus.UNKNOWN) {
                status = TargetStatus.UP;
                lastStatusChangeAt = now;
            }
            return Optional.empty();
        }

        consecutiveFailures++;
        if (status != TargetStatus.DOWN && consecutiveFailures >= failureThreshold) {
            status = TargetStatus.DOWN;
            lastStatusChangeAt = now;
            return Optional.of(AlertType.DOWN);
        }
        return Optional.empty();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public HttpMethodType getHttpMethod() {
        return httpMethod;
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public int getExpectedStatus() {
        return expectedStatus;
    }

    public String getExpectedBodyContains() {
        return expectedBodyContains;
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public boolean isActive() {
        return active;
    }

    public TargetStatus getStatus() {
        return status;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public Instant getLastStatusChangeAt() {
        return lastStatusChangeAt;
    }

    public Instant getNextCheckAt() {
        return nextCheckAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
