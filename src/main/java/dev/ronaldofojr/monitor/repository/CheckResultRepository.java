package dev.ronaldofojr.monitor.repository;

import dev.ronaldofojr.monitor.domain.CheckResult;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CheckResultRepository extends JpaRepository<CheckResult, UUID> {

    Page<CheckResult> findByTargetIdOrderByCheckedAtDesc(UUID targetId, Pageable pageable);

    Page<CheckResult> findByTargetIdAndSuccessFalseOrderByCheckedAtDesc(UUID targetId, Pageable pageable);

    interface Stats {
        long getTotal();

        long getOk();

        Double getAvgLatency();

        Double getP95Latency();
    }

    // latencia so das checagens que deram certo: um timeout de 5s nao e "latencia" do servico
    @Query(value = """
            SELECT count(*) AS total,
                   count(*) FILTER (WHERE success) AS ok,
                   avg(latency_ms) FILTER (WHERE success) AS avgLatency,
                   percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms) FILTER (WHERE success) AS p95Latency
            FROM check_results
            WHERE target_id = :targetId AND checked_at >= :since
            """, nativeQuery = true)
    Stats statsSince(@Param("targetId") UUID targetId, @Param("since") Instant since);

    @Transactional
    @Modifying
    @Query("delete from CheckResult r where r.checkedAt < :limit")
    int deleteOlderThan(@Param("limit") Instant limit);
}
