package dev.ronaldofojr.monitor.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ronaldofojr.monitor.FakeHttpServer;
import dev.ronaldofojr.monitor.IntegrationTestBase;
import dev.ronaldofojr.monitor.domain.Alert;
import dev.ronaldofojr.monitor.domain.CheckResult;
import dev.ronaldofojr.monitor.domain.HttpMethodType;
import dev.ronaldofojr.monitor.domain.Target;
import dev.ronaldofojr.monitor.domain.TargetStatus;
import dev.ronaldofojr.monitor.repository.AlertRepository;
import dev.ronaldofojr.monitor.service.TargetService.TargetSettings;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Contexto proprio: o webhook configurado aponta para uma porta onde ninguem escuta. */
@SpringBootTest(properties = "monitor.scheduler.enabled=false")
class UnreachableWebhookIT {

    private static final FakeHttpServer TARGET = new FakeHttpServer();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", IntegrationTestBase.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", IntegrationTestBase.POSTGRES::getUsername);
        registry.add("spring.datasource.password", IntegrationTestBase.POSTGRES::getPassword);
        registry.add("monitor.alerts.webhook-url", () -> "http://127.0.0.1:1/hook");
    }

    @Autowired
    TargetService targetService;
    @Autowired
    CheckService checkService;
    @Autowired
    AlertRepository alerts;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE alerts, check_results, targets CASCADE");
    }

    @AfterAll
    static void stop() {
        TARGET.close();
    }

    @Test
    void anUnreachableWebhookDoesNotBreakTheCheckAndKeepsTheAlertPending() {
        TARGET.status = 500;
        Target target = targetService.create(new TargetSettings("api-webhook-fora", TARGET.url("/health"),
                HttpMethodType.GET, 600, 1000, 200, null, 1));

        CheckResult result = checkService.runCheck(target.getId());

        assertThat(result.isSuccess()).isFalse();
        assertThat(targetService.get(target.getId()).getStatus()).isEqualTo(TargetStatus.DOWN);
        Alert alert = alerts.findAll().get(0);
        assertThat(alert.getNotifiedAt()).isNull();
        assertThat(alert.getNotifyAttempts()).isEqualTo(1);
        assertThat(alert.getLastNotifyError()).isNotBlank();
    }
}
