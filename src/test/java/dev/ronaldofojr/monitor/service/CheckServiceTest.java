package dev.ronaldofojr.monitor.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class CheckServiceTest {

    @Test
    void humanizesDurations() {
        assertThat(CheckService.humanize(Duration.ofSeconds(42))).isEqualTo("42s");
        assertThat(CheckService.humanize(Duration.ofSeconds(125))).isEqualTo("2min 5s");
        assertThat(CheckService.humanize(Duration.ofMinutes(90))).isEqualTo("1h 30min");
        assertThat(CheckService.humanize(Duration.ofHours(5))).isEqualTo("5h 0min");
    }

    @Test
    void negativeDurationsBecomeZero() {
        assertThat(CheckService.humanize(Duration.ofSeconds(-10))).isEqualTo("0s");
    }
}
