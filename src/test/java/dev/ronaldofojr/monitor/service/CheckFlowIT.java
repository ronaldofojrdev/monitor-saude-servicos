package dev.ronaldofojr.monitor.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ronaldofojr.monitor.IntegrationTestBase;
import dev.ronaldofojr.monitor.domain.Alert;
import dev.ronaldofojr.monitor.domain.AlertType;
import dev.ronaldofojr.monitor.domain.CheckResult;
import dev.ronaldofojr.monitor.domain.HttpMethodType;
import dev.ronaldofojr.monitor.domain.Target;
import dev.ronaldofojr.monitor.domain.TargetStatus;
import dev.ronaldofojr.monitor.repository.AlertRepository;
import dev.ronaldofojr.monitor.service.TargetService.TargetSettings;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CheckFlowIT extends IntegrationTestBase {

    @Autowired
    CheckService checkService;
    @Autowired
    AlertNotifier notifier;
    @Autowired
    AlertRepository alerts;

    private Target reload(Target t) {
        return targetService.get(t.getId());
    }

    @Test
    void healthyServiceIsRecordedAsUpWithoutAlerts() {
        Target target = newTarget("api-saudavel", 3);

        CheckResult result = checkService.runCheck(target.getId());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getHttpStatus()).isEqualTo(200);
        assertThat(reload(target).getStatus()).isEqualTo(TargetStatus.UP);
        assertThat(count("check_results")).isEqualTo(1);
        assertThat(count("alerts")).isZero();
        assertThat(WEBHOOK.hits.get()).isZero();
    }

    @Test
    void alertsOnlyWhenTheThresholdIsReachedAndOnlyOnce() {
        Target target = newTarget("api-instavel", 3);
        checkService.runCheck(target.getId()); // UP

        TARGET.status = 500;
        checkService.runCheck(target.getId());
        checkService.runCheck(target.getId());
        assertThat(reload(target).getStatus()).isEqualTo(TargetStatus.UP);
        assertThat(count("alerts")).isZero();

        checkService.runCheck(target.getId()); // terceira falha seguida
        assertThat(reload(target).getStatus()).isEqualTo(TargetStatus.DOWN);
        assertThat(count("alerts")).isEqualTo(1);

        checkService.runCheck(target.getId());
        checkService.runCheck(target.getId());
        assertThat(count("alerts")).as("continuar fora do ar nao gera alerta repetido").isEqualTo(1);
        assertThat(count("check_results")).isEqualTo(6);
    }

    @Test
    void recoveryCreatesASecondAlertAndResetsTheFailureCounter() {
        Target target = newTarget("api-recupera", 2);
        TARGET.status = 503;
        checkService.runCheck(target.getId());
        checkService.runCheck(target.getId());
        assertThat(reload(target).getStatus()).isEqualTo(TargetStatus.DOWN);

        TARGET.status = 200;
        checkService.runCheck(target.getId());

        Target after = reload(target);
        assertThat(after.getStatus()).isEqualTo(TargetStatus.UP);
        assertThat(after.getConsecutiveFailures()).isZero();
        List<Alert> all = alerts.findAll();
        assertThat(all).extracting(Alert::getType).containsExactlyInAnyOrder(AlertType.DOWN, AlertType.RECOVERED);
        assertThat(all.stream().filter(a -> a.getType() == AlertType.RECOVERED).findFirst().orElseThrow().getMessage())
                .contains("api-recupera voltou ao ar");
    }

    @Test
    void failureReasonsAreStoredInTheHistory() {
        Target target = targetService.create(new TargetSettings("api-motivos", TARGET.url("/health"),
                HttpMethodType.GET, 600, 300, 200, "tudo certo", 5));

        TARGET.status = 502;
        assertThat(checkService.runCheck(target.getId()).getError()).isEqualTo("Status 502 (esperado 200)");

        TARGET.status = 200;
        TARGET.body = "algo diferente";
        assertThat(checkService.runCheck(target.getId()).getError()).contains("nao contem o texto esperado");

        TARGET.delayMs = 1000;
        CheckResult timeout = checkService.runCheck(target.getId());
        assertThat(timeout.isSuccess()).isFalse();
        assertThat(timeout.getHttpStatus()).isNull();
        assertThat(timeout.getError()).isEqualTo("Timeout apos 300ms");
    }

    @Test
    void webhookReceivesTheDownAndRecoveredAlertsWithTheExpectedFields() {
        Target target = newTarget("api-webhook", 1);
        TARGET.status = 500;
        checkService.runCheck(target.getId());

        assertThat(WEBHOOK.hits.get()).isEqualTo(1);
        assertThat(WEBHOOK.receivedMethods).containsExactly("POST");
        String payload = WEBHOOK.receivedBodies.get(0);
        assertThat(payload).contains("\"type\":\"DOWN\"").contains("\"target\":\"api-webhook\"")
                .contains("\"url\":\"" + TARGET.url("/health") + "\"").contains("\"occurredAt\"")
                .contains("fora do ar");
        Alert alert = alerts.findAll().get(0);
        assertThat(alert.getNotifiedAt()).isNotNull();
        assertThat(alert.getNotifyAttempts()).isEqualTo(1);

        TARGET.status = 200;
        checkService.runCheck(target.getId());
        assertThat(WEBHOOK.receivedBodies.get(1)).contains("\"type\":\"RECOVERED\"");
    }

    @Test
    void failedWebhookKeepsTheAlertPendingAndTheRetryDeliversItLater() {
        WEBHOOK.status = 503;
        Target target = newTarget("api-retry", 1);
        TARGET.status = 500;
        checkService.runCheck(target.getId());

        Alert pending = alerts.findAll().get(0);
        assertThat(pending.getNotifiedAt()).isNull();
        assertThat(pending.getNotifyAttempts()).isEqualTo(1);
        assertThat(pending.getLastNotifyError()).isEqualTo("Webhook respondeu HTTP 503");
        assertThat(count("check_results")).as("o historico nao depende da entrega do alerta").isEqualTo(1);

        assertThat(notifier.retryPending()).isZero();
        assertThat(alerts.findAll().get(0).getNotifyAttempts()).isEqualTo(2);

        WEBHOOK.status = 200;
        assertThat(notifier.retryPending()).isEqualTo(1);
        Alert delivered = alerts.findAll().get(0);
        assertThat(delivered.getNotifiedAt()).isNotNull();
        assertThat(delivered.getLastNotifyError()).isNull();

        assertThat(notifier.retryPending()).as("nada mais pendente").isZero();
    }

    @Test
    void retryStopsAfterTheMaximumNumberOfAttempts() {
        WEBHOOK.status = 500;
        Target target = newTarget("api-desiste", 1);
        TARGET.status = 500;
        checkService.runCheck(target.getId()); // tentativa 1
        for (int i = 0; i < 10; i++) {
            notifier.retryPending();
        }

        assertThat(alerts.findAll().get(0).getNotifyAttempts()).isEqualTo(5);
        int hitsBefore = WEBHOOK.hits.get();
        notifier.retryPending();
        assertThat(WEBHOOK.hits.get()).isEqualTo(hitsBefore);
    }
}
