package dev.ronaldofojr.monitor.metrics;

import dev.ronaldofojr.monitor.check.CheckOutcome;
import dev.ronaldofojr.monitor.domain.Target;
import dev.ronaldofojr.monitor.domain.TargetStatus;
import dev.ronaldofojr.monitor.repository.TargetRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Metricas Micrometer expostas em /actuator/prometheus:
 *   monitor_target_up{target}        1 = UP, 0 = DOWN
 *   monitor_checks_total{target,result}   contador de checagens (success/failure)
 *   monitor_check_latency{target}    tempo de resposta das checagens bem-sucedidas
 *   monitor_targets_down             quantos alvos ativos estao DOWN agora
 */
@Component
public class MonitorMetrics {

    private final MeterRegistry registry;
    private final TargetRepository targets;
    private final Map<UUID, AtomicInteger> upGauges = new ConcurrentHashMap<>();
    private final Map<UUID, String> namesById = new ConcurrentHashMap<>();

    public MonitorMetrics(MeterRegistry registry, TargetRepository targets) {
        this.registry = registry;
        this.targets = targets;
        Gauge.builder("monitor.targets.down", () -> targets.countByActiveTrueAndStatus(TargetStatus.DOWN))
                .description("Alvos ativos que estao fora do ar")
                .register(registry);
    }

    /** Ao subir, restaura o gauge dos alvos que ja tinham estado conhecido (senao ele so voltaria na proxima checagem). */
    @EventListener(ApplicationReadyEvent.class)
    public void restoreGauges() {
        targets.findAll().stream()
                .filter(t -> t.getStatus() != TargetStatus.UNKNOWN)
                .forEach(this::updateState);
    }

    public void recordCheck(Target target, CheckOutcome outcome) {
        Tags tags = Tags.of("target", target.getName());
        registry.counter("monitor.checks", tags.and("result", outcome.success() ? "success" : "failure")).increment();
        if (outcome.success()) {
            registry.timer("monitor.check.latency", tags).record(outcome.latencyMs(), TimeUnit.MILLISECONDS);
        }
        updateState(target);
    }

    public void updateState(Target target) {
        namesById.put(target.getId(), target.getName());
        AtomicInteger value = upGauges.computeIfAbsent(target.getId(), id -> {
            AtomicInteger holder = new AtomicInteger();
            Gauge.builder("monitor.target.up", holder, AtomicInteger::get)
                    .description("1 se o alvo esta UP, 0 se esta DOWN")
                    .tag("target", target.getName())
                    .register(registry);
            return holder;
        });
        value.set(target.getStatus() == TargetStatus.DOWN ? 0 : 1);
    }

    /** Remove as metricas de um alvo (apagado ou renomeado) para nao ficarem series orfas. */
    public void forget(UUID targetId) {
        upGauges.remove(targetId);
        String name = namesById.remove(targetId);
        if (name == null) {
            return;
        }
        for (String metric : List.of("monitor.target.up", "monitor.checks", "monitor.check.latency")) {
            registry.find(metric).tag("target", name).meters().forEach((Meter m) -> registry.remove(m));
        }
    }
}
