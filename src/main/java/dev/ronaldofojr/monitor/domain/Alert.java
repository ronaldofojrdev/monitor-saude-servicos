package dev.ronaldofojr.monitor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Alerta gerado quando um alvo muda de estado. notifiedAt fica vazio ate a notificacao ser entregue. */
@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    private UUID id;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertType type;

    @Column(nullable = false)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "notified_at")
    private Instant notifiedAt;

    @Column(name = "notify_attempts", nullable = false)
    private int notifyAttempts;

    @Column(name = "last_notify_error")
    private String lastNotifyError;

    protected Alert() {
    }

    public Alert(UUID targetId, AlertType type, String message, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.targetId = targetId;
        this.type = type;
        this.message = message.length() <= 500 ? message : message.substring(0, 500);
        this.createdAt = createdAt;
    }

    public void markNotified(Instant when) {
        this.notifyAttempts++;
        this.notifiedAt = when;
        this.lastNotifyError = null;
    }

    public void markNotifyFailed(String error) {
        this.notifyAttempts++;
        this.lastNotifyError = error == null || error.length() <= 300 ? error : error.substring(0, 300);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public AlertType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getNotifiedAt() {
        return notifiedAt;
    }

    public int getNotifyAttempts() {
        return notifyAttempts;
    }

    public String getLastNotifyError() {
        return lastNotifyError;
    }
}
