package dev.ronaldofojr.monitor.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ronaldofojr.monitor.FakeHttpServer;
import dev.ronaldofojr.monitor.domain.HttpMethodType;
import java.net.ServerSocket;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Testa o checker contra um servidor HTTP de verdade (local), sem banco. */
class JdkHttpCheckerTest {

    private static FakeHttpServer server;
    private final JdkHttpChecker checker = new JdkHttpChecker();

    @BeforeAll
    static void start() {
        server = new FakeHttpServer();
    }

    @AfterAll
    static void stop() {
        server.close();
    }

    @BeforeEach
    void reset() {
        server.reset();
    }

    private CheckSpec spec(HttpMethodType method, int timeoutMs, int expectedStatus, String bodyText) {
        return new CheckSpec(server.url("/health"), method, timeoutMs, expectedStatus, bodyText);
    }

    @Test
    void successWhenStatusMatches() {
        CheckOutcome outcome = checker.check(spec(HttpMethodType.GET, 2000, 200, null));

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.httpStatus()).isEqualTo(200);
        assertThat(outcome.error()).isNull();
        assertThat(outcome.latencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void failsWhenStatusIsDifferentFromTheExpected() {
        server.status = 503;

        CheckOutcome outcome = checker.check(spec(HttpMethodType.GET, 2000, 200, null));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.httpStatus()).isEqualTo(503);
        assertThat(outcome.error()).isEqualTo("Status 503 (esperado 200)");
    }

    @Test
    void anotherExpectedStatusCanBeConfigured() {
        server.status = 401;

        assertThat(checker.check(spec(HttpMethodType.GET, 2000, 401, null)).success()).isTrue();
    }

    @Test
    void checksTheBodyText() {
        server.body = "{\"status\":\"UP\"}";

        assertThat(checker.check(spec(HttpMethodType.GET, 2000, 200, "\"status\":\"UP\"")).success()).isTrue();

        server.body = "{\"status\":\"DOWN\"}";
        CheckOutcome outcome = checker.check(spec(HttpMethodType.GET, 2000, 200, "\"status\":\"UP\""));
        assertThat(outcome.success()).isFalse();
        assertThat(outcome.error()).contains("nao contem o texto esperado");
    }

    @Test
    void headRequestsWorkAndSkipTheBody() {
        CheckOutcome outcome = checker.check(spec(HttpMethodType.HEAD, 2000, 200, null));

        assertThat(outcome.success()).isTrue();
        assertThat(server.receivedMethods).contains("HEAD");
    }

    @Test
    void timeoutBecomesAFailureWithAClearMessage() {
        server.delayMs = 1500;

        CheckOutcome outcome = checker.check(spec(HttpMethodType.GET, 300, 200, null));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.httpStatus()).isNull();
        assertThat(outcome.error()).isEqualTo("Timeout apos 300ms");
        assertThat(outcome.latencyMs()).isBetween(250L, 1400L);
    }

    @Test
    void connectionRefusedBecomesAFailureInsteadOfAnException() throws Exception {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        CheckSpec spec = new CheckSpec("http://127.0.0.1:" + freePort + "/", HttpMethodType.GET, 1000, 200, null);

        CheckOutcome outcome = checker.check(spec);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.httpStatus()).isNull();
        assertThat(outcome.error()).isEqualTo("Conexao recusada ou host inacessivel");
    }

    @Test
    void unresolvableHostBecomesAFailure() {
        CheckSpec spec = new CheckSpec("http://host-que-nao-existe.invalid/", HttpMethodType.GET, 2000, 200, null);

        CheckOutcome outcome = checker.check(spec);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.error()).isNotBlank();
    }
}
