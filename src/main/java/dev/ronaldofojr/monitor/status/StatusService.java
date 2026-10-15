package dev.ronaldofojr.monitor.status;

import dev.ronaldofojr.monitor.domain.Target;
import dev.ronaldofojr.monitor.domain.TargetStatus;
import dev.ronaldofojr.monitor.repository.CheckResultRepository;
import dev.ronaldofojr.monitor.repository.CheckResultRepository.Stats;
import dev.ronaldofojr.monitor.repository.TargetRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatusService {

    /** O que a pagina publica mostra de cada servico. De proposito nao inclui a URL (pode ser interna). */
    public record ServiceStatus(String name, TargetStatus status, Instant lastCheckedAt, Double uptime24h) {
    }

    public record StatusPage(OverallStatus status, Instant updatedAt, List<ServiceStatus> services) {
    }

    private final TargetRepository targets;
    private final CheckResultRepository results;
    private final Clock clock;

    public StatusService(TargetRepository targets, CheckResultRepository results, Clock clock) {
        this.targets = targets;
        this.results = results;
        this.clock = clock;
    }

    /** So alvos ativos: um alvo pausado nao esta sendo monitorado, entao nao aparece nem pesa na situacao geral. */
    @Transactional(readOnly = true)
    public StatusPage current() {
        Instant now = clock.instant();
        Instant since = now.minus(Duration.ofHours(24));
        List<Target> active = targets.findAllByOrderByNameAsc().stream().filter(Target::isActive).toList();

        List<ServiceStatus> services = active.stream().map(t -> {
            Stats stats = results.statsSince(t.getId(), since);
            Double uptime = stats.getTotal() == 0 ? null : Math.round(stats.getOk() * 10000.0 / stats.getTotal()) / 100.0;
            return new ServiceStatus(t.getName(), t.getStatus(), t.getLastCheckedAt(), uptime);
        }).toList();

        return new StatusPage(OverallStatus.of(active.stream().map(Target::getStatus).toList()), now, services);
    }
}
