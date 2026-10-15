package dev.ronaldofojr.monitor.check;

import dev.ronaldofojr.monitor.domain.HttpMethodType;

/** O que o checker precisa saber para fazer uma checagem. */
public record CheckSpec(String url, HttpMethodType method, int timeoutMs, int expectedStatus,
                        String expectedBodyContains) {
}
