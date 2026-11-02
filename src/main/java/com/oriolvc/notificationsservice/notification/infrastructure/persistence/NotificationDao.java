package com.oriolvc.notificationsservice.notification.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NotificationDao extends JpaRepository<NotificationDbo, Long>, JpaSpecificationExecutor<NotificationDbo> {

    /**
     * Inserts the notification unless one exists for the same (type, event key, customer). The unique constraint
     * decides, so concurrent inserts of the same event store it once (RFC 7.1, AC-9).
     *
     * @return the new id; empty if it already existed
     */
    @Query(nativeQuery = true, value = """
                    INSERT INTO notifications
                        (notification_type_id, event_key, customer_id, parameters, outcome, created_at, created_by)
                    VALUES
                        (:notificationTypeId, :eventKey, :customerId, CAST(:parameters AS jsonb), :outcome,
                         :createdAt, :createdBy)
                    ON CONFLICT ON CONSTRAINT uq_notifications_type_event_customer DO NOTHING
                    RETURNING id
                    """)
    Optional<Long> insertIfAbsent(
            @Param("notificationTypeId") long notificationTypeId,
            @Param("eventKey") String eventKey,
            @Param("customerId") long customerId,
            @Param("parameters") String parameters,
            @Param("outcome") String outcome,
            @Param("createdAt") Instant createdAt,
            @Param("createdBy") String createdBy);

    @Query("""
            SELECT n.id FROM NotificationDbo n
            WHERE n.notificationTypeId = :notificationTypeId AND n.eventKey = :eventKey AND n.customerId = :customerId
            """)
    Optional<Long> findId(
            @Param("notificationTypeId") long notificationTypeId,
            @Param("eventKey") String eventKey,
            @Param("customerId") long customerId);

    @EntityGraph(attributePaths = "deliveries")
    List<NotificationDbo> findAllByIdIn(Collection<Long> ids);

    @EntityGraph(attributePaths = "deliveries")
    Optional<NotificationDbo> findWithDeliveriesById(long id);
}
