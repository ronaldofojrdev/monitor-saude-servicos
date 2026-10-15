package dev.ronaldofojr.monitor.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TargetTest {

    private final Instant t0 = Instant.parse("2026-03-01T10:00:00Z");

    private Target target(int threshold) {
        return new Target("API", "http://exemplo.com/health", HttpMethodType.GET, 60, 5000, 200, null, threshold, t0);
    }

    @Test
    void startsUnknownAndBecomesUpOnTheFirstSuccessWithoutAlert() {
        Target target = target(3);
        assertThat(target.getStatus()).isEqualTo(TargetStatus.UNKNOWN);

        Optional<AlertType> alert = target.recordOutcome(true, t0);

        assertThat(alert).isEmpty();
        assertThat(target.getStatus()).isEqualTo(TargetStatus.UP);
        assertThat(target.getLastCheckedAt()).isEqualTo(t0);
    }

    @Test
    void goesDownOnlyAfterTheThresholdOfConsecutiveFailures() {
        Target target = target(3);
        target.recordOutcome(true, t0);

        assertThat(target.recordOutcome(false, t0.plusSeconds(60))).isEmpty();
        assertThat(target.recordOutcome(false, t0.plusSeconds(120))).isEmpty();
        assertThat(target.getStatus()).isEqualTo(TargetStatus.UP);

        assertThat(target.recordOutcome(false, t0.plusSeconds(180))).contains(AlertType.DOWN);
        assertThat(target.getStatus()).isEqualTo(TargetStatus.DOWN);
        assertThat(target.getConsecutiveFailures()).isEqualTo(3);
        assertThat(target.getLastStatusChangeAt()).isEqualTo(t0.plusSeconds(180));
    }

    @Test
    void anIsolatedFailureBetweenSuccessesNeverAlerts() {
        Target target = target(3);

        for (int i = 0; i < 10; i++) {
            assertThat(target.recordOutcome(true, t0.plusSeconds(i * 60L))).isEmpty();
            assertThat(target.recordOutcome(false, t0.plusSeconds(i * 60L + 30))).isEmpty();
        }

        assertThat(target.getStatus()).isEqualTo(TargetStatus.UP);
    }

    @Test
    void stayingDownDoesNotAlertAgain() {
        Target target = target(2);
        target.recordOutcome(false, t0);
        assertThat(target.recordOutcome(false, t0.plusSeconds(60))).contains(AlertType.DOWN);

        assertThat(target.recordOutcome(false, t0.plusSeconds(120))).isEmpty();
        assertThat(target.recordOutcome(false, t0.plusSeconds(180))).isEmpty();
        assertThat(target.getConsecutiveFailures()).isEqualTo(4);
    }

    @Test
    void recoversOnTheFirstSuccessAndResetsTheCounter() {
        Target target = target(2);
        target.recordOutcome(false, t0);
        target.recordOutcome(false, t0.plusSeconds(60));

        assertThat(target.recordOutcome(true, t0.plusSeconds(120))).contains(AlertType.RECOVERED);

        assertThat(target.getStatus()).isEqualTo(TargetStatus.UP);
        assertThat(target.getConsecutiveFailures()).isZero();
        assertThat(target.getLastStatusChangeAt()).isEqualTo(t0.plusSeconds(120));
    }

    @Test
    void thresholdOneAlertsOnTheVeryFirstFailure() {
        Target target = target(1);

        assertThat(target.recordOutcome(false, t0)).contains(AlertType.DOWN);
    }

    @Test
    void firstChecksFailingFromUnknownAlsoReachDownAfterTheThreshold() {
        Target target = target(2);

        assertThat(target.recordOutcome(false, t0)).isEmpty();
        assertThat(target.recordOutcome(false, t0.plusSeconds(60))).contains(AlertType.DOWN);
    }

    @Test
    void blankBodyTextIsStoredAsNull() {
        Target target = target(3);

        target.updateSettings("API", "http://exemplo.com", HttpMethodType.GET, 60, 5000, 200, "   ", 3, true);

        assertThat(target.getExpectedBodyContains()).isNull();
    }
}
