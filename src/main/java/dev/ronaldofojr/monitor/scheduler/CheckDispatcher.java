package dev.ronaldofojr.monitor.scheduler;

import dev.ronaldofojr.monitor.config.MonitorProperties;
import dev.ronaldofojr.monitor.repository.CheckResultRepository;
import dev.ronaldofojr.monitor.repository.TargetRepository;
import dev.ronaldofojr.monitor.service.CheckService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** O que o scheduler faz a cada tick, separado do agendamento para os testes poderem chamar direto. */
@Component
public class CheckDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CheckDispatcher.class);

    private final TargetRepository targets;
    private final CheckResultRepository results;
    private final CheckService checkService;
    private final Executor checkExecutor;
    private final SchedulerHeartbeat heartbeat;
    private final MonitorProperties properties;
    private final Clock clock;

    public CheckDispatcher(TargetRepository targets, CheckResultRepository results, CheckService checkService,
                           Executor checkExecutor, SchedulerHeartbeat heartbeat, MonitorProperties properties,
                           Clock clock) {
        this.targets = targets;
        this.results = results;
        this.checkService = checkService;
        this.checkExecutor = checkExecutor;
        this.heartbeat = heartbeat;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Procura os alvos com checagem vencida, reserva cada um e dispara a checagem em paralelo.
     * A reserva (claimDue) garante que nenhum alvo e checado duas vezes, mesmo com dois ticks
     * sobrepostos ou duas instancias do monitor rodando.
     *
     * @return quantas checagens foram disparadas
     */
    public int tick() {
        Instant now = clock.instant();
        heartbeat.beat(now);

        int started = 0;
        for (UUID id : targets.findDueIds(now)) {
            if (targets.claimDue(id, now) == 0) {
                continue; // outra instancia (ou outro tick) ja reservou
            }
            try {
                checkExecutor.execute(() -> runSafely(id));
                started++;
            } catch (RejectedExecutionException e) {
                log.error("Fila de checagens cheia, o alvo {} sera checado no proximo ciclo", id);
            }
        }
        return started;
    }

    private void runSafely(UUID targetId) {
        try {
            checkService.runCheck(targetId);
        } catch (RuntimeException e) {
            // um alvo com problema nao pode matar a thread do pool nem afetar os outros
            log.error("Falha ao checar o alvo {}", targetId, e);
        }
    }

    /** Apaga o historico mais antigo que a retencao configurada. */
    public int purgeOldHistory() {
        Instant limit = clock.instant().minus(Duration.ofDays(properties.history().retentionDays()));
        int deleted = results.deleteOlderThan(limit);
        if (deleted > 0) {
            log.info("Historico: {} resultados com mais de {} dias apagados", deleted, properties.history().retentionDays());
        }
        return deleted;
    }
}
