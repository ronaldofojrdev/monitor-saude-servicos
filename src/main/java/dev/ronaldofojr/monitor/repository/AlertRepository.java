package dev.ronaldofojr.monitor.repository;

import dev.ronaldofojr.monitor.domain.Alert;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    Page<Alert> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Alert> findByTargetIdOrderByCreatedAtDesc(UUID targetId, Pageable pageable);

    List<Alert> findByNotifiedAtIsNullAndNotifyAttemptsLessThanOrderByCreatedAtAsc(int maxAttempts);
}
