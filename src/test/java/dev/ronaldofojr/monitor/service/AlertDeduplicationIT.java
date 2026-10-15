package dev.ronaldofojr.monitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.ronaldofojr.monitor.IntegrationTestBase;
import dev.ronaldofojr.monitor.domain.Alert;
import dev.ronaldofojr.monitor.domain.AlertType;
import dev.ronaldofojr.monitor.domain.Target;
import dev.ronaldofojr.monitor.repository.AlertRepository;
import dev.ronaldofojr.monitor.scheduler.CheckDispatcher;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** O alerta existe so quando o estado muda. Continuar falhando (ou oscilar) nao pode gerar alerta repetido. */
class AlertDeduplicationIT extends IntegrationTestBase {

    @Autowired
    CheckService checkService;
    @Autowired
    CheckDispatcher dispatcher;
    @Autowired
    AlertRepository alerts;

    private List<AlertType> alertTypesInOrder() {
        return alerts.findAll().stream().sorted(Comparator.comparing(Alert::getCreatedAt)).map(Alert::getType).toList();
    }

    private void fail(Target target, int times) {
        TARGET.status = 500;
        for (int i = 0; i < times; i++) {
            checkService.runCheck(target.getId());
        }
    }

    private void succeed(Target target) {
        TARGET.status = 200;
        checkService.runCheck(target.getId());
    }

    @Test
    void tenConsecutiveFailuresProduceOneAlertAndOneWebhook() {
        Target target = newTarget("dez-falhas", 3);

        fail(target, 10);

        assertThat(alertTypesInOrder()).containsExactly(AlertType.DOWN);
        assertThat(WEBHOOK.hits.get()).isEqualTo(1);
        assertThat(count("check_results")).isEqualTo(10);
        assertThat(targetService.get(target.getId()).getConsecutiveFailures()).isEqualTo(10);
    }

    @Test
    void consecutiveFailuresCheckedByTheSchedulerAlsoAlertOnlyOnce() {
        Target target = newTarget("pelo-scheduler", 2);
        TARGET.status = 500;

        for (int round = 1; round <= 5; round++) {
            jdbc.update("UPDATE targets SET next_check_at = ? WHERE id = ?",
                    Timestamp.from(Instant.now().minusSeconds(5)), target.getId());
            assertThat(dispatcher.tick()).isEqualTo(1);
            int expected = round;
            await().atMost(Duration.ofSeconds(10)).until(() -> count("check_results") == expected);
        }

        assertThat(alertTypesInOrder()).containsExactly(AlertType.DOWN);
        assertThat(WEBHOOK.hits.get()).isEqualTo(1);
    }

    @Test
    void flappingBelowTheThresholdNeverAlerts() {
        Target target = newTarget("oscila", 2);

        for (int i = 0; i < 6; i++) {
            fail(target, 1);
            succeed(target);
        }

        assertThat(count("alerts")).isZero();
        assertThat(WEBHOOK.hits.get()).isZero();
        assertThat(targetService.get(target.getId()).getConsecutiveFailures()).isZero();
    }

    @Test
    void aSuccessResetsTheCountSoTheThresholdMustBeReachedAgain() {
        Target target = newTarget("zera-contagem", 3);

        fail(target, 2);
        succeed(target);
        fail(target, 2);

        assertThat(count("alerts")).as("2 + 2 falhas, mas nunca 3 seguidas").isZero();

        fail(target, 1);
        assertThat(alertTypesInOrder()).containsExactly(AlertType.DOWN);
    }

    @Test
    void aSecondIncidentAfterTheRecoveryAlertsAgain() {
        Target target = newTarget("dois-incidentes", 2);

        fail(target, 3);       // incidente 1
        succeed(target);       // recuperou
        fail(target, 1);       // 1 falha: ainda abaixo do limite
        assertThat(alertTypesInOrder()).containsExactly(AlertType.DOWN, AlertType.RECOVERED);

        fail(target, 1);       // 2 falhas seguidas: incidente 2

        assertThat(alertTypesInOrder()).containsExactly(AlertType.DOWN, AlertType.RECOVERED, AlertType.DOWN);
        assertThat(WEBHOOK.hits.get()).isEqualTo(3);
    }

    @Test
    void successfulChecksWhileUpNeverAlert() {
        Target target = newTarget("sempre-ok", 1);

        for (int i = 0; i < 5; i++) {
            succeed(target);
        }

        assertThat(count("alerts")).isZero();
    }

    @Test
    void failingWebhookDeliveryDoesNotMakeTheNextFailuresCreateNewAlerts() {
        WEBHOOK.status = 503;
        Target target = newTarget("webhook-fora", 1);

        fail(target, 4);

        assertThat(count("alerts")).as("a entrega falhou, mas o alerta e um so").isEqualTo(1);
        assertThat(alerts.findAll().get(0).getNotifiedAt()).isNull();
    }

    @Test
    void eachTargetHasItsOwnIncidentTracking() {
        Target a = newTarget("alvo-a", 2);
        Target b = newTarget("alvo-b", 2);

        fail(a, 2);
        succeed(b);

        assertThat(alerts.findAll()).hasSize(1);
        assertThat(alerts.findAll().get(0).getTargetId()).isEqualTo(a.getId());
        assertThat(targetService.get(b.getId()).getConsecutiveFailures()).isZero();
    }
}
