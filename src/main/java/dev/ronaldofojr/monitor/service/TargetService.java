package dev.ronaldofojr.monitor.service;

import dev.ronaldofojr.monitor.domain.Alert;
import dev.ronaldofojr.monitor.domain.CheckResult;
import dev.ronaldofojr.monitor.domain.HttpMethodType;
import dev.ronaldofojr.monitor.domain.Target;
import dev.ronaldofojr.monitor.metrics.MonitorMetrics;
import dev.ronaldofojr.monitor.repository.AlertRepository;
import dev.ronaldofojr.monitor.repository.CheckResultRepository;
import dev.ronaldofojr.monitor.repository.CheckResultRepository.Stats;
import dev.ronaldofojr.monitor.repository.TargetRepository;
import dev.ronaldofojr.monitor.service.Exceptions.BusinessRuleException;
import dev.ronaldofojr.monitor.service.Exceptions.ConflictException;
import dev.ronaldofojr.monitor.service.Exceptions.NotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TargetService {

    public record TargetSettings(String name, String url, HttpMethodType method, int intervalSeconds, int timeoutMs,
                                 int expectedStatus, String expectedBodyContains, int failureThreshold) {
    }

    public record StatsView(int hours, long totalChecks, long successfulChecks, Double uptimePercent,
                            Double avgLatencyMs, Double p95LatencyMs) {
    }

    private static final int MAX_STATS_HOURS = 24 * 30;

    private final TargetRepository targets;
    private final CheckResultRepository results;
    private final AlertRepository alerts;
    private final CheckService checkService;
    private final MonitorMetrics metrics;
    private final Clock clock;

    public TargetService(TargetRepository targets, CheckResultRepository results, AlertRepository alerts,
                         CheckService checkService, MonitorMetrics metrics, Clock clock) {
        this.targets = targets;
        this.results = results;
        this.alerts = alerts;
        this.checkService = checkService;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public Target create(TargetSettings s) {
        validate(s);
        if (targets.existsByName(s.name())) {
            throw new ConflictException("Ja existe um alvo chamado '" + s.name() + "'");
        }
        try {
            return targets.saveAndFlush(new Target(s.name(), s.url().trim(), s.method(), s.intervalSeconds(),
                    s.timeoutMs(), s.expectedStatus(), s.expectedBodyContains(), s.failureThreshold(), clock.instant()));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Ja existe um alvo chamado '" + s.name() + "'");
        }
    }

    @Transactional
    public Target update(UUID id, TargetSettings s, boolean active) {
        validate(s);
        Target target = get(id);
        if (targets.existsByNameAndIdNot(s.name(), id)) {
            throw new ConflictException("Ja existe um alvo chamado '" + s.name() + "'");
        }
        target.updateSettings(s.name(), s.url().trim(), s.method(), s.intervalSeconds(), s.timeoutMs(),
                s.expectedStatus(), s.expectedBodyContains(), s.failureThreshold(), active);
        Target saved = targets.saveAndFlush(target);
        // o nome e uma tag das metricas: renomear sem limpar deixaria series antigas penduradas
        metrics.forget(id);
        metrics.updateState(saved);
        return saved;
    }

    /** Pausar duas vezes nao e erro. */
    @Transactional
    public Target pause(UUID id) {
        Target target = get(id);
        target.pause();
        return targets.save(target);
    }

    /** Retoma o monitoramento e agenda uma checagem imediata (o proximo tick ja checa). */
    @Transactional
    public Target resume(UUID id) {
        Target target = get(id);
        target.resume();
        targets.saveAndFlush(target);
        targets.scheduleNow(id, clock.instant());
        return get(id);
    }

    @Transactional
    public void delete(UUID id) {
        Target target = get(id);
        targets.delete(target); // o historico e os alertas saem junto (ON DELETE CASCADE)
        metrics.forget(id);
    }

    @Transactional(readOnly = true)
    public Target get(UUID id) {
        return targets.findById(id).orElseThrow(() -> new NotFoundException("Alvo", id));
    }

    @Transactional(readOnly = true)
    public List<Target> list() {
        return targets.findAllByOrderByNameAsc();
    }

    /** Checa agora, fora do agendamento, e devolve o resultado. */
    public CheckResult checkNow(UUID id) {
        return checkService.runCheck(id);
    }

    @Transactional(readOnly = true)
    public Page<CheckResult> history(UUID id, boolean failuresOnly, Pageable pageable) {
        get(id);
        return failuresOnly
                ? results.findByTargetIdAndSuccessFalseOrderByCheckedAtDesc(id, pageable)
                : results.findByTargetIdOrderByCheckedAtDesc(id, pageable);
    }

    @Transactional(readOnly = true)
    public StatsView stats(UUID id, int hours) {
        if (hours < 1 || hours > MAX_STATS_HOURS) {
            throw new BusinessRuleException("hours deve estar entre 1 e " + MAX_STATS_HOURS);
        }
        get(id);
        Stats stats = results.statsSince(id, clock.instant().minus(Duration.ofHours(hours)));
        Double uptime = stats.getTotal() == 0 ? null : round(stats.getOk() * 100.0 / stats.getTotal());
        return new StatsView(hours, stats.getTotal(), stats.getOk(), uptime,
                stats.getAvgLatency() == null ? null : round(stats.getAvgLatency()),
                stats.getP95Latency() == null ? null : round(stats.getP95Latency()));
    }

    @Transactional(readOnly = true)
    public Page<Alert> alerts(UUID targetId, Pageable pageable) {
        if (targetId == null) {
            return alerts.findAllByOrderByCreatedAtDesc(pageable);
        }
        get(targetId);
        return alerts.findByTargetIdOrderByCreatedAtDesc(targetId, pageable);
    }

    private void validate(TargetSettings s) {
        UrlValidator.validate(s.url());
        if (s.method() == HttpMethodType.HEAD && s.expectedBodyContains() != null && !s.expectedBodyContains().isBlank()) {
            throw new BusinessRuleException("HEAD nao tem corpo: nao da para conferir expectedBodyContains");
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
