package dev.ronaldofojr.monitor.scheduler;

import dev.ronaldofojr.monitor.service.AlertNotifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** So agenda; a logica esta no CheckDispatcher e no AlertNotifier. */
@Component
public class ScheduledJobs {

    private final CheckDispatcher dispatcher;
    private final AlertNotifier notifier;

    public ScheduledJobs(CheckDispatcher dispatcher, AlertNotifier notifier) {
        this.dispatcher = dispatcher;
        this.notifier = notifier;
    }

    @Scheduled(fixedDelayString = "${monitor.scheduler.tick-ms}")
    public void tick() {
        dispatcher.tick();
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void retryAlerts() {
        notifier.retryPending();
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void purgeHistory() {
        dispatcher.purgeOldHistory();
    }
}
