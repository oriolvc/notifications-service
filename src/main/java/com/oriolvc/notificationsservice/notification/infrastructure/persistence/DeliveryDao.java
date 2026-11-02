package com.oriolvc.notificationsservice.notification.infrastructure.persistence;

import com.oriolvc.notificationsservice.notification.domain.DeliveryStatus;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface DeliveryDao extends JpaRepository<DeliveryDbo, Long> {

    /**
     * The pending delivery due longest ago, locked until the transaction ends. Deliveries locked by another dispatcher
     * are skipped rather than waited for, so concurrent dispatchers never send the same delivery (RFC 9).
     */
    @Query(nativeQuery = true, value = """
                    SELECT * FROM notification_deliveries
                    WHERE status = 'PENDING' AND next_attempt_at <= :now
                    ORDER BY next_attempt_at, id
                    LIMIT 1
                    FOR UPDATE SKIP LOCKED
                    """)
    Optional<DeliveryDbo> lockNextDue(@Param("now") Instant now);

    long countByStatus(DeliveryStatus status);

    @Query("SELECT min(d.createdAt) FROM DeliveryDbo d WHERE d.status = :status")
    Optional<Instant> findOldestCreatedAtByStatus(@Param("status") DeliveryStatus status);
}
