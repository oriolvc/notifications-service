package com.oriolvc.notificationsservice.notification.infrastructure.persistence;

import com.oriolvc.notificationsservice.notification.domain.DeliveryStatus;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Row of {@code notification_deliveries}. The list and member ids reference the distribution context's tables by id
 * only; the address and content are snapshots.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "notification_deliveries")
class DeliveryDbo {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "notification_deliveries_id_seq")
    @SequenceGenerator(
            name = "notification_deliveries_id_seq",
            sequenceName = "notification_deliveries_id_seq",
            allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false, updatable = false)
    private NotificationDbo notification;

    @Column(name = "distribution_list_id", nullable = false, updatable = false)
    private long distributionListId;

    @Column(name = "distribution_list_member_id", nullable = false, updatable = false)
    private long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, updatable = false)
    private Channel channel;

    @Column(name = "address", nullable = false, updatable = false)
    private String address;

    @Column(name = "template_id", nullable = false, updatable = false)
    private long templateId;

    @Column(name = "subject", updatable = false)
    private String subject;

    @Column(name = "body", nullable = false, updatable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DeliveryStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "provider_message_id")
    private String providerMessageId;

    /** New attempts are persisted with the delivery when it is flushed. Append-only. */
    @OneToMany(mappedBy = "delivery", cascade = CascadeType.PERSIST)
    @OrderBy("attemptNumber")
    private List<DeliveryAttemptDbo> attempts = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeliveryDbo() {}

    DeliveryDbo(
            final NotificationDbo notification,
            final long distributionListId,
            final long memberId,
            final Channel channel,
            final String address,
            final long templateId,
            final String subject,
            final String body,
            final DeliveryStatus status,
            final int attemptCount,
            final Instant nextAttemptAt) {
        this.notification = notification;
        this.distributionListId = distributionListId;
        this.memberId = memberId;
        this.channel = channel;
        this.address = address;
        this.templateId = templateId;
        this.subject = subject;
        this.body = body;
        this.status = status;
        this.attemptCount = attemptCount;
        this.nextAttemptAt = nextAttemptAt;
    }

    Long getId() {
        return id;
    }

    long getDistributionListId() {
        return distributionListId;
    }

    long getMemberId() {
        return memberId;
    }

    Channel getChannel() {
        return channel;
    }

    String getAddress() {
        return address;
    }

    long getTemplateId() {
        return templateId;
    }

    String getSubject() {
        return subject;
    }

    String getBody() {
        return body;
    }

    DeliveryStatus getStatus() {
        return status;
    }

    int getAttemptCount() {
        return attemptCount;
    }

    Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    Instant getSentAt() {
        return sentAt;
    }

    String getProviderMessageId() {
        return providerMessageId;
    }

    List<DeliveryAttemptDbo> getAttempts() {
        return attempts;
    }

    /** The dispatch state; everything else is fixed at creation. */
    void setDispatchState(
            final DeliveryStatus status,
            final int attemptCount,
            final Instant nextAttemptAt,
            final Instant sentAt,
            final String providerMessageId) {
        this.status = status;
        this.attemptCount = attemptCount;
        this.nextAttemptAt = nextAttemptAt;
        this.sentAt = sentAt;
        this.providerMessageId = providerMessageId;
    }
}
