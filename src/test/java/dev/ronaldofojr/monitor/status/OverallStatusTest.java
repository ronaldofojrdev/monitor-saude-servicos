package dev.ronaldofojr.monitor.status;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ronaldofojr.monitor.domain.TargetStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class OverallStatusTest {

    @Test
    void allUpIsOperational() {
        assertThat(OverallStatus.of(List.of(TargetStatus.UP, TargetStatus.UP))).isEqualTo(OverallStatus.OPERATIONAL);
    }

    @Test
    void someDownIsDegraded() {
        assertThat(OverallStatus.of(List.of(TargetStatus.UP, TargetStatus.DOWN, TargetStatus.UP))).isEqualTo(OverallStatus.DEGRADED);
    }

    @Test
    void allDownIsMajorOutage() {
        assertThat(OverallStatus.of(List.of(TargetStatus.DOWN, TargetStatus.DOWN))).isEqualTo(OverallStatus.MAJOR_OUTAGE);
        assertThat(OverallStatus.of(List.of(TargetStatus.DOWN))).isEqualTo(OverallStatus.MAJOR_OUTAGE);
    }

    @Test
    void noTargetsIsNoData() {
        assertThat(OverallStatus.of(List.of())).isEqualTo(OverallStatus.NO_DATA);
    }

    @Test
    void targetsThatWereNeverCheckedAreLeftOutOfTheCount() {
        assertThat(OverallStatus.of(List.of(TargetStatus.UNKNOWN, TargetStatus.UNKNOWN))).isEqualTo(OverallStatus.NO_DATA);
        assertThat(OverallStatus.of(List.of(TargetStatus.UNKNOWN, TargetStatus.UP))).isEqualTo(OverallStatus.OPERATIONAL);
        assertThat(OverallStatus.of(List.of(TargetStatus.UNKNOWN, TargetStatus.DOWN))).isEqualTo(OverallStatus.MAJOR_OUTAGE);
    }
}
