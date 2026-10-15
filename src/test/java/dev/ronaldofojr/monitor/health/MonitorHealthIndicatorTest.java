package dev.ronaldofojr.monitor.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.ronaldofojr.monitor.config.MonitorProperties;
import dev.ronaldofojr.monitor.domain.TargetStatus;
import dev.ronaldofojr.monitor.repository.TargetRepository;
import dev.ronaldofojr.monitor.scheduler.SchedulerHeartbeat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

class MonitorHealthIndicatorTest {

    private final Instant now = Instant.parse("2026-03-01T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final TargetRepository targets = mock(TargetRepository.class);
    private final SchedulerHeartbeat heartbeat = new SchedulerHeartbeat();

    private MonitorHealthIndicator indicator(boolean schedulerEnabled) {
        MonitorProperties properties = new MonitorProperties(
                new MonitorProperties.Scheduler(schedulerEnabled, 5000, 4),
                new MonitorProperties.History(30),
                new MonitorProperties.Alerts("", 5000, 5));
        when(targets.countByActiveTrue()).thenReturn(5L);
        when(targets.countByActiveTrueAndStatus(TargetStatus.DOWN)).thenReturn(2L);
        return new MonitorHealthIndicator(targets, heartbeat, properties, clock);
    }

    @Test
    void upWithRecentHeartbeatAndTargetsDownDoNotMakeTheMonitorUnhealthy() {
        heartbeat.beat(now.minusSeconds(4));

        Health health = indicator(true).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("activeTargets", 5L).containsEntry("targetsDown", 2L)
                .containsEntry("scheduler", "rodando");
    }

    @Test
    void downWhenTheSchedulerStoppedTicking() {
        heartbeat.beat(now.minus(Duration.ofMinutes(10)));

        Health health = indicator(true).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("scheduler").toString()).contains("parado");
    }

    @Test
    void stillUpBeforeTheFirstTickSoStartupIsNotReportedAsFailure() {
        Health health = indicator(true).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("lastTick", "nunca");
    }

    @Test
    void upWhenTheSchedulerIsDisabledByConfiguration() {
        Health health = indicator(false).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("scheduler").toString()).contains("desligado");
    }
}
