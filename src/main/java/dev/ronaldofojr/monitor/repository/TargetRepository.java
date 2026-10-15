package dev.ronaldofojr.monitor.repository;

import dev.ronaldofojr.monitor.domain.Target;
import dev.ronaldofojr.monitor.domain.TargetStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TargetRepository extends JpaRepository<Target, UUID> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    List<Target> findAllByOrderByNameAsc();

    long countByActiveTrueAndStatus(TargetStatus status);

    long countByActiveTrue();

    @Query("select t.id from Target t where t.active = true and t.nextCheckAt <= :now")
    List<UUID> findDueIds(@Param("now") Instant now);

    /**
     * "Reserva" o alvo para esta instancia: so uma consegue, porque o WHERE exige que ele ainda esteja vencido.
     * Duas instancias do monitor (ou dois ticks sobrepostos) nunca checam o mesmo alvo ao mesmo tempo.
     * Devolve 1 se reservou, 0 se outro ja reservou.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE targets
            SET next_check_at = CAST(:now AS timestamptz) + (interval_seconds * INTERVAL '1 second')
            WHERE id = :id AND active = TRUE AND next_check_at <= CAST(:now AS timestamptz)
            """, nativeQuery = true)
    int claimDue(@Param("id") UUID id, @Param("now") Instant now);

    /** Faz o alvo ficar "vencido" agora, para o scheduler checa-lo no proximo tick (usado ao retomar). */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE targets SET next_check_at = CAST(:now AS timestamptz) WHERE id = :id", nativeQuery = true)
    int scheduleNow(@Param("id") UUID id, @Param("now") Instant now);
}
