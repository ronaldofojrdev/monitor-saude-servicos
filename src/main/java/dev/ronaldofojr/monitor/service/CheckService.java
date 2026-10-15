package dev.ronaldofojr.monitor.service;

import dev.ronaldofojr.monitor.check.CheckOutcome;
import dev.ronaldofojr.monitor.check.CheckSpec;
import dev.ronaldofojr.monitor.check.HttpChecker;
import dev.ronaldofojr.monitor.domain.Alert;
import dev.ronaldofojr.monitor.domain.AlertType;
import dev.ronaldofojr.monitor.domain.CheckResult;
import dev.ronaldofojr.monitor.domain.Target;
import dev.ronaldofojr.monitor.metrics.MonitorMetrics;
import dev.ronaldofojr.monitor.repository.AlertRepository;
import dev.ronaldofojr.monitor.repository.CheckResultRepository;
import dev.ronaldofojr.monitor.repository.TargetRepository;
import dev.ronaldofojr.monitor.service.Exceptions.NotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Executa uma checagem, grava o historico, atualiza o estado do alvo e cria o alerta quando o estado muda. */
@Service
public class CheckService {

    private static final Logger log = LoggerFactory.getLogger(CheckService.class);

    private record Persisted(CheckResult result, Target target) {
    }

    private final TargetRepository targets;
    private final CheckResultRepository results;
    private final AlertRepository alerts;
    private final HttpChecker checker;
    private final MonitorMetrics metrics;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;

    public CheckService(TargetRepository targets, CheckResultRepository results, AlertRepository alerts,
                        HttpChecker checker, MonitorMetrics metrics, ApplicationEventPublisher events,
                        TransactionTemplate tx, Clock clock) {
        this.targets = targets;
        this.results = results;
        this.alerts = alerts;
        this.checker = checker;
        this.metrics = metrics;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
    }

    /** A chamada HTTP (lenta, de rede) fica fora de transacao: so a gravacao do resultado abre uma. */
    public CheckResult runCheck(UUID targetId) {
        Target target = targets.findById(targetId).orElseThrow(() -> new NotFoundException("Alvo", targetId));
        CheckOutcome outcome = checker.check(new CheckSpec(target.getUrl(), target.getHttpMethod(),
                target.getTimeoutMs(), target.getExpectedStatus(), target.getExpectedBodyContains()));

        Persisted persisted = tx.execute(status -> persist(targetId, outcome));
        metrics.recordCheck(persisted.target(), outcome);
        return persisted.result();
    }

    private Persisted persist(UUID targetId, CheckOutcome outcome) {
        // recarrega dentro da transacao: o estado pode ter mudado enquanto a requisicao HTTP estava em andamento
        Target target = targets.findById(targetId).orElseThrow(() -> new NotFoundException("Alvo", targetId));
        Instant now = clock.instant();
        Instant previousChange = target.getLastStatusChangeAt();

        CheckResult result = results.save(new CheckResult(targetId, now, outcome.success(), outcome.httpStatus(),
                outcome.latencyMs(), outcome.error()));

        Optional<AlertType> transition = target.recordOutcome(outcome.success(), now);
        targets.save(target);

        transition.ifPresent(type -> {
            Alert alert = alerts.save(new Alert(targetId, type, messageFor(type, target, outcome, previousChange, now), now));
            events.publishEvent(new AlertCreated(alert.getId()));
            log.warn("Alerta {} para o alvo {}: {}", type, target.getName(), alert.getMessage());
        });
        return new Persisted(result, target);
    }

    private String messageFor(AlertType type, Target target, CheckOutcome outcome, Instant previousChange, Instant now) {
        if (type == AlertType.DOWN) {
            return "%s esta fora do ar: %d falhas seguidas (ultimo erro: %s)"
                    .formatted(target.getName(), target.getFailureThreshold(), outcome.error());
        }
        String downtime = previousChange == null ? "tempo desconhecido" : humanize(Duration.between(previousChange, now));
        return "%s voltou ao ar (ficou fora por %s)".formatted(target.getName(), downtime);
    }

    static String humanize(Duration duration) {
        long seconds = Math.max(0, duration.getSeconds());
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "min " + (seconds % 60) + "s";
        }
        return (minutes / 60) + "h " + (minutes % 60) + "min";
    }
}
