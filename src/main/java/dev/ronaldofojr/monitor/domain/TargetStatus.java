package dev.ronaldofojr.monitor.domain;

public enum TargetStatus {
    /** Ainda nao foi checado. */
    UNKNOWN,
    UP,
    /** Falhou N vezes seguidas (N = failureThreshold do alvo). */
    DOWN
}
