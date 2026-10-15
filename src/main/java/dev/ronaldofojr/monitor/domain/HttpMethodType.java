package dev.ronaldofojr.monitor.domain;

public enum HttpMethodType {
    GET,
    /** Mais leve, mas nao permite conferir o corpo da resposta. */
    HEAD
}
