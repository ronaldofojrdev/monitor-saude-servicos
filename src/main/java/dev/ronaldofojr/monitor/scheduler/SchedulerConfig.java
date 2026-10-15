package dev.ronaldofojr.monitor.scheduler;

import dev.ronaldofojr.monitor.config.MonitorProperties;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class SchedulerConfig {

    /** Pool das checagens: cada checagem fica presa ate o timeout, entao varias rodam em paralelo. */
    @Bean
    ThreadPoolTaskExecutor checkExecutor(MonitorProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.scheduler().threads());
        executor.setMaxPoolSize(properties.scheduler().threads());
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("check-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        return executor;
    }

    /** Os testes desligam o agendamento (monitor.scheduler.enabled=false) e chamam o CheckDispatcher direto. */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "monitor.scheduler.enabled", havingValue = "true", matchIfMissing = true)
    static class SchedulingEnabled {
    }
}
