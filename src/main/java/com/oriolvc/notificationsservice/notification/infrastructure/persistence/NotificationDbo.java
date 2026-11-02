package com.oriolvc.notificationsservice.notification.infrastructure.persistence;

import com.oriolvc.notificationsservice.notification.domain.NotificationOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Row of {@code notifications}. Immutable, and inserted with {@code ON CONFLICT DO NOTHING} by
 * {@link NotificationDao#insertIfAbsent}, so JPA only reads it (and its id and audit columns are set by that insert).
 */
@Entity
@Immutable
@Table(name = "notifications")
class NotificationDbo {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "notification_type_id", nullable = false)
    private long notificationTypeId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "event_key", nullable = false, length = 64)
    private String eventKey;

    @Column(name = "customer_id", nullable = false)
    private long customerId;

    /** JSON text ({@code jsonb} column); parsed by the mapper. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", nullable = false)
    private String parameters;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false)
    private NotificationOutcome outcome;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @OneToMany(mappedBy = "notification")
    @OrderBy("id")
    private List<DeliveryDbo> deliveries = new ArrayList<>();

    Long getId() {
        return id;
    }

    long getNotificationTypeId() {
        return notificationTypeId;
    }

    String getEventKey() {
        return eventKey;
    }

    long getCustomerId() {
        return customerId;
    }

    String getParameters() {
        return parameters;
    }

    NotificationOutcome getOutcome() {
        return outcome;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    String getCreatedBy() {
        return createdBy;
    }

    List<DeliveryDbo> getDeliveries() {
        return deliveries;
    }
}
