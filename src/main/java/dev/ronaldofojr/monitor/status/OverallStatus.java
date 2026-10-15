package dev.ronaldofojr.monitor.status;

import dev.ronaldofojr.monitor.domain.TargetStatus;
import java.util.Collection;

/** Situacao geral exibida na pagina de status. */
public enum OverallStatus {
    /** Todos os servicos monitorados estao no ar. */
    OPERATIONAL,
    /** Alguns servicos estao fora do ar. */
    DEGRADED,
    /** Todos os servicos estao fora do ar. */
    MAJOR_OUTAGE,
    /** Ainda nao ha nenhum servico com estado conhecido (sem alvos, todos pausados ou sem primeira checagem). */
    NO_DATA;

    /** Alvos UNKNOWN (nunca checados) nao entram na conta: ainda nao se sabe se estao bem ou mal. */
    public static OverallStatus of(Collection<TargetStatus> statuses) {
        long up = statuses.stream().filter(s -> s == TargetStatus.UP).count();
        long down = statuses.stream().filter(s -> s == TargetStatus.DOWN).count();
        if (up + down == 0) {
            return NO_DATA;
        }
        if (down == 0) {
            return OPERATIONAL;
        }
        return up == 0 ? MAJOR_OUTAGE : DEGRADED;
    }
}
