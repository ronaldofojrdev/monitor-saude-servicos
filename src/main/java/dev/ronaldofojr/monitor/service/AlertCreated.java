package dev.ronaldofojr.monitor.service;

import java.util.UUID;

/** Evento interno publicado dentro da transacao da checagem; a notificacao so sai depois do commit. */
public record AlertCreated(UUID alertId) {
}
