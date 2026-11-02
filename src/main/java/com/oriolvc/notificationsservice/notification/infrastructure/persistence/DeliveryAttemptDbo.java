package com.oriolvc.notificationsservice.notification.infrastructure.persistence;

import com.oriolvc.notificationsservice.notification.domain.AttemptOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/** Row of {@code delivery_attempts}: append-only history of every send. */
@Entity
@Immutable
@Table(name = "delivery_attempts")
class DeliveryAttemptDbo {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "delivery_attempts_id_seq")
    @SequenceGenerator(name = "delivery_attempts_id_seq", sequenceName = "delivery_attempts_id_seq", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false)
    private DeliveryDbo delivery;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false)
    private AttemptOutcome outcome;

    @Column(name = "provider_message_id")
    private String providerMessageId;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    protected DeliveryAttemptDbo() {}

    DeliveryAttemptDbo(
            final DeliveryDbo delivery,
            final int attemptNumber,
            final Instant attemptedAt,
            final AttemptOutcome outcome,
            final String providerMessageId,
            final String errorCode,
            final String errorMessage) {
        this.delivery = delivery;
        this.attemptNumber = attemptNumber;
        this.attemptedAt = attemptedAt;
        this.outcome = outcome;
        this.providerMessageId = providerMessageId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    int getAttemptNumber() {
        return attemptNumber;
    }

    Instant getAttemptedAt() {
        return attemptedAt;
    }

    AttemptOutcome getOutcome() {
        return outcome;
    }

    String getProviderMessageId() {
        return providerMessageId;
    }

    String getErrorCode() {
        return errorCode;
    }

    String getErrorMessage() {
        return errorMessage;
    }
}
