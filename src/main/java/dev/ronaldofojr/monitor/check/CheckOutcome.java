package dev.ronaldofojr.monitor.check;

/** Resultado bruto de uma checagem. httpStatus e nulo quando nem chegou a ter resposta (timeout, conexao recusada). */
public record CheckOutcome(boolean success, Integer httpStatus, long latencyMs, String error) {

    public static CheckOutcome ok(int httpStatus, long latencyMs) {
        return new CheckOutcome(true, httpStatus, latencyMs, null);
    }

    public static CheckOutcome failed(Integer httpStatus, long latencyMs, String error) {
        return new CheckOutcome(false, httpStatus, latencyMs, error);
    }
}
