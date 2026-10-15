package dev.ronaldofojr.monitor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ronaldofojr.monitor.config.MonitorProperties;
import dev.ronaldofojr.monitor.domain.Alert;
import dev.ronaldofojr.monitor.domain.Target;
import dev.ronaldofojr.monitor.repository.AlertRepository;
import dev.ronaldofojr.monitor.repository.TargetRepository;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Entrega os alertas. Com ALERT_WEBHOOK_URL configurada, faz um POST em JSON; sem ela, so registra no log.
 * Se a entrega falha o alerta continua pendente (notifiedAt vazio) e o job de retry tenta de novo,
 * ate max-notify-attempts.
 */
@Component
public class AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(AlertNotifier.class);

    private final AlertRepository alerts;
    private final TargetRepository targets;
    private final MonitorProperties properties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public AlertNotifier(AlertRepository alerts, TargetRepository targets, MonitorProperties properties,
                         ObjectMapper objectMapper, PlatformTransactionManager transactionManager, Clock clock) {
        this.alerts = alerts;
        this.targets = targets;
        this.properties = properties;
        this.objectMapper = objectMapper;
        // REQUIRES_NEW: o onAlertCreated roda em AFTER_COMMIT, quando a transacao antiga ja acabou mas ainda esta
        // "ligada" a thread. Com o padrao (REQUIRED) a gravacao entraria nela e nunca seria commitada.
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    // so depois do commit: nunca avisamos de um alerta que nao chegou a ser gravado
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAlertCreated(AlertCreated event) {
        try {
            deliver(event.alertId());
        } catch (RuntimeException e) {
            log.error("Falha inesperada ao notificar o alerta {}; o retry assume", event.alertId(), e);
        }
    }

    /** Reenvia os alertas que ainda nao foram entregues. Devolve quantos foram entregues agora. */
    public int retryPending() {
        List<Alert> pending = alerts.findByNotifiedAtIsNullAndNotifyAttemptsLessThanOrderByCreatedAtAsc(
                properties.alerts().maxNotifyAttempts());
        int delivered = 0;
        for (Alert alert : pending) {
            if (deliver(alert.getId())) {
                delivered++;
            }
        }
        return delivered;
    }

    public boolean deliver(UUID alertId) {
        Alert alert = alerts.findById(alertId).orElse(null);
        if (alert == null || alert.getNotifiedAt() != null) {
            return false;
        }
        String webhookUrl = properties.alerts().webhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.info("[ALERTA {}] {}", alert.getType(), alert.getMessage());
            tx.executeWithoutResult(s -> alerts.findById(alertId).ifPresent(a -> a.markNotified(clock.instant())));
            return true;
        }

        String error = null;
        try {
            error = post(webhookUrl, alert);
        } catch (IOException e) {
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            error = "Interrompido";
        }

        String finalError = error;
        tx.executeWithoutResult(s -> alerts.findById(alertId).ifPresent(a -> {
            if (finalError == null) {
                a.markNotified(clock.instant());
            } else {
                a.markNotifyFailed(finalError);
            }
        }));
        if (error != null) {
            log.warn("Nao consegui entregar o alerta {} no webhook: {}", alertId, error);
        }
        return error == null;
    }

    /** @return null se entregou (2xx), senao a descricao do erro */
    private String post(String url, Alert alert) throws IOException, InterruptedException {
        Target target = targets.findById(alert.getTargetId()).orElse(null);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", alert.getType().name());
        payload.put("target", target == null ? null : target.getName());
        payload.put("url", target == null ? null : target.getUrl());
        payload.put("message", alert.getMessage());
        payload.put("occurredAt", alert.getCreatedAt().toString());

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "Nao foi possivel montar o JSON: " + e.getMessage();
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(properties.alerts().webhookTimeoutMs()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        int status = response.statusCode();
        return status >= 200 && status < 300 ? null : "Webhook respondeu HTTP " + status;
    }
}
