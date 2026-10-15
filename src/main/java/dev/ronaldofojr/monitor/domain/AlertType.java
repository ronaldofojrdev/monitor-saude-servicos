package dev.ronaldofojr.monitor.domain;

public enum AlertType {
    /** O alvo passou de UP para DOWN. */
    DOWN,
    /** O alvo voltou de DOWN para UP. */
    RECOVERED
}
