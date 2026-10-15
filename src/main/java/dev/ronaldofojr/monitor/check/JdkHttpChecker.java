package dev.ronaldofojr.monitor.check;

import dev.ronaldofojr.monitor.domain.HttpMethodType;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class JdkHttpChecker implements HttpChecker {

    // so lemos o comeco do corpo: o monitor nao deve baixar uma resposta gigante para procurar um texto
    private static final int MAX_BODY_BYTES = 64 * 1024;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public CheckOutcome check(CheckSpec spec) {
        long start = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(spec.url()))
                    .timeout(Duration.ofMillis(spec.timeoutMs()))
                    .header("User-Agent", "monitor-saude-servicos/0.1")
                    .method(spec.method().name(), HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            boolean checkBody = spec.expectedBodyContains() != null && spec.method() == HttpMethodType.GET;
            String body = null;
            try (InputStream stream = response.body()) {
                if (checkBody) {
                    body = new String(stream.readNBytes(MAX_BODY_BYTES), StandardCharsets.UTF_8);
                }
            }
            long latency = elapsedMs(start);

            int status = response.statusCode();
            if (status != spec.expectedStatus()) {
                return CheckOutcome.failed(status, latency, "Status " + status + " (esperado " + spec.expectedStatus() + ")");
            }
            if (checkBody && !body.contains(spec.expectedBodyContains())) {
                return CheckOutcome.failed(status, latency, "O corpo da resposta nao contem o texto esperado");
            }
            return CheckOutcome.ok(status, latency);
        } catch (HttpTimeoutException e) {
            return CheckOutcome.failed(null, elapsedMs(start), "Timeout apos " + spec.timeoutMs() + "ms");
        } catch (IOException e) {
            return CheckOutcome.failed(null, elapsedMs(start), describe(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CheckOutcome.failed(null, elapsedMs(start), "Checagem interrompida");
        } catch (IllegalArgumentException e) {
            return CheckOutcome.failed(null, elapsedMs(start), "URL invalida: " + e.getMessage());
        }
    }

    // a ConnectException do JDK costuma vir sem mensagem ("ConnectException: null"), o que nao ajuda ninguem no alerta
    private static String describe(IOException e) {
        if (e instanceof ConnectException) {
            return "Conexao recusada ou host inacessivel";
        }
        String name = e.getClass().getSimpleName();
        return e.getMessage() == null ? name : name + ": " + e.getMessage();
    }

    private static long elapsedMs(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
