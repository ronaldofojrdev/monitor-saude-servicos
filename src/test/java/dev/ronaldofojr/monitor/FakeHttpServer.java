package dev.ronaldofojr.monitor;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servidor HTTP de teste (JDK puro) com resposta configuravel. Faz o papel do servico monitorado
 * e tambem do endpoint de webhook que recebe os alertas.
 */
public class FakeHttpServer implements AutoCloseable {

    private final HttpServer server;
    public volatile int status = 200;
    public volatile String body = "ok";
    public volatile long delayMs = 0;
    public final AtomicInteger hits = new AtomicInteger();
    public final List<String> receivedBodies = new CopyOnWriteArrayList<>();
    public final List<String> receivedMethods = new CopyOnWriteArrayList<>();

    public FakeHttpServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            receivedMethods.add(exchange.getRequestMethod());
            receivedBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            boolean head = "HEAD".equals(exchange.getRequestMethod());
            exchange.sendResponseHeaders(status, head || payload.length == 0 ? -1 : payload.length);
            if (!head && payload.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(payload);
                }
            }
            exchange.close();
        });
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
    }

    public String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void reset() {
        status = 200;
        body = "ok";
        delayMs = 0;
        hits.set(0);
        receivedBodies.clear();
        receivedMethods.clear();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
