package dev.ronaldofojr.monitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "monitor")
public record MonitorProperties(Scheduler scheduler, History history, Alerts alerts) {

    public record Scheduler(boolean enabled, long tickMs, int threads) {
    }

    public record History(int retentionDays) {
    }

    public record Alerts(String webhookUrl, int webhookTimeoutMs, int maxNotifyAttempts) {
    }
}
