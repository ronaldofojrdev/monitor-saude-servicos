package dev.ronaldofojr.monitor.scheduler;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** Guarda quando o scheduler rodou pela ultima vez. O health check usa isso para detectar scheduler parado. */
@Component
public class SchedulerHeartbeat {

    private final AtomicReference<Instant> lastTick = new AtomicReference<>();

    public void beat(Instant when) {
        lastTick.set(when);
    }

    public Instant lastTick() {
        return lastTick.get();
    }
}
