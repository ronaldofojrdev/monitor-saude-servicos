package dev.ronaldofojr.monitor.health;

import dev.ronaldofojr.monitor.config.MonitorProperties;
import dev.ronaldofojr.monitor.domain.TargetStatus;
import dev.ronaldofojr.monitor.repository.TargetRepository;
import dev.ronaldofojr.monitor.scheduler.SchedulerHeartbeat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Aparece em /actuator/health como "monitor". Fica DOWN se o scheduler parou de rodar (o monitor estaria
 * "no ar" mas sem checar nada, que e pior do que cair de vez). Alvos DOWN NAO derrubam o health do monitor:
 * o monitor esta saudavel, quem caiu foi o servico monitorado.
 */
@Component("monitor")
public class MonitorHealthIndicator implements HealthIndicator {

    private final TargetRepository targets;
    private final SchedulerHeartbeat heartbeat;
    private final MonitorProperties properties;
    private final Clock clock;

    public MonitorHealthIndicator(TargetRepository targets, SchedulerHeartbeat heartbeat,
                                  MonitorProperties properties, Clock clock) {
        this.targets = targets;
        this.heartbeat = heartbeat;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up()
                .withDetail("activeTargets", targets.countByActiveTrue())
                .withDetail("targetsDown", targets.countByActiveTrueAndStatus(TargetStatus.DOWN));

        if (!properties.scheduler().enabled()) {
            return builder.withDetail("scheduler", "desligado por configuracao").build();
        }

        Instant last = heartbeat.lastTick();
        Duration tolerance = Duration.ofMillis(properties.scheduler().tickMs() * 3).plusSeconds(30);
        builder.withDetail("lastTick", last == null ? "nunca" : last.toString());
        if (last != null && Duration.between(last, clock.instant()).compareTo(tolerance) > 0) {
            return builder.down().withDetail("scheduler", "parado: sem tick ha mais de " + tolerance.toSeconds() + "s").build();
        }
        // na partida ainda nao houve o primeiro tick: nao e motivo para marcar DOWN
        return builder.withDetail("scheduler", "rodando").build();
    }
}
