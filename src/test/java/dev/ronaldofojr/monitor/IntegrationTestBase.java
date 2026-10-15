package dev.ronaldofojr.monitor;

import dev.ronaldofojr.monitor.domain.HttpMethodType;
import dev.ronaldofojr.monitor.domain.Target;
import dev.ronaldofojr.monitor.service.TargetService;
import dev.ronaldofojr.monitor.service.TargetService.TargetSettings;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Postgres real (Testcontainers) + dois servidores HTTP locais:
 * TARGET faz o papel do servico monitorado e WEBHOOK recebe os alertas.
 * O agendamento fica desligado: os testes chamam o dispatcher/servicos direto.
 */
@SpringBootTest(properties = "monitor.scheduler.enabled=false")
@AutoConfigureMockMvc
@AutoConfigureObservability
public abstract class IntegrationTestBase {

    public static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    protected static final FakeHttpServer TARGET = new FakeHttpServer();
    protected static final FakeHttpServer WEBHOOK = new FakeHttpServer();

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("monitor.alerts.webhook-url", () -> WEBHOOK.url("/hook"));
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected TargetService targetService;

    @BeforeEach
    void cleanState() {
        jdbc.execute("TRUNCATE alerts, check_results, targets CASCADE");
        TARGET.reset();
        WEBHOOK.reset();
    }

    protected TargetSettings settings(String name, int failureThreshold) {
        // intervalo de 10 min: nada de checagem "espontanea" no meio do teste
        return new TargetSettings(name, TARGET.url("/health"), HttpMethodType.GET, 600, 1000, 200, null, failureThreshold);
    }

    protected Target newTarget(String name, int failureThreshold) {
        return targetService.create(settings(name, failureThreshold));
    }

    protected int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
