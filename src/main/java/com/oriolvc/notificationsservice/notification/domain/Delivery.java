package com.oriolvc.notificationsservice.notification.domain;

import com.oriolvc.notificationsservice.shared.domain.Channel;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One message to one recipient. The address and the rendered content are snapshots taken when the notification was
 * accepted, so later list changes do not affect it (AC-15, AC-16) and the record shows exactly what was sent (D-7).
 *
 * <p>Created with its notification; from then on it is dispatched on its own: every attempt is recorded, a retryable
 * error is retried after a growing backoff, and a permanent error or the last allowed attempt fails it (RFC 9).
 */
public final class Delivery {

    public static final int MAX_ATTEMPTS = 5;

    /** Wait before attempt n + 1 after a retryable error on attempt n. */
    static final List<Duration> BACKOFF =
            List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofMinutes(60));

    private final Long id;
    private final long distributionListId;
    private final long memberId;
    private final Channel channel;
    private final String address;
    private final long templateId;
    private final RenderedMessage content;
    private final Instant createdAt;
    private DeliveryStatus status;
    private int attemptCount;
    private Instant nextAttemptAt;
    private Instant sentAt;
    private String providerMessageId;
    private final List<DeliveryAttempt> attempts;

    private Delivery(
            final Long id,
            final long distributionListId,
            final long memberId,
            final Channel channel,
            final String address,
            final long templateId,
            final RenderedMessage content,
            final Instant createdAt,
            final DeliveryStatus status,
            final int attemptCount,
            final Instant nextAttemptAt,
            final Instant sentAt,
            final String providerMessageId,
            final List<DeliveryAttempt> attempts) {
        this.id = id;
        this.distributionListId = distributionListId;
        this.memberId = memberId;
        this.channel = Objects.requireNonNull(channel, "channel");
        this.address = Objects.requireNonNull(address, "address");
        this.templateId = templateId;
        this.content = Objects.requireNonNull(content, "content");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.status = Objects.requireNonNull(status, "status");
        this.attemptCount = attemptCount;
        this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        this.sentAt = sentAt;
        this.providerMessageId = providerMessageId;
        this.attempts = new ArrayList<>(attempts);
    }

    /** A new delivery, due immediately. */
    static Delivery pending(
            final Recipient recipient, final Template template, final RenderedMessage content, final Instant now) {
        return new Delivery(
                null,
                recipient.distributionListId(),
                recipient.memberId(),
                recipient.channel(),
                recipient.address(),
                template.id(),
                content,
                now,
                DeliveryStatus.PENDING,
                0,
                now,
                null,
                null,
                List.of());
    }

    /** Rebuilds a stored delivery. */
    public static Delivery restore(
            final long id,
            final long distributionListId,
            final long memberId,
            final Channel channel,
            final String address,
            final long templateId,
            final RenderedMessage content,
            final Instant createdAt,
            final DeliveryStatus status,
            final Instant nextAttemptAt,
            final Instant sentAt,
            final String providerMessageId,
            final List<DeliveryAttempt> attempts) {
        return new Delivery(
                id,
                distributionListId,
                memberId,
                channel,
                address,
                templateId,
                content,
                createdAt,
                status,
                attempts.size(),
                nextAttemptAt,
                sentAt,
                providerMessageId,
                attempts);
    }

    /**
     * Records the outcome of sending it now: {@link DeliveryStatus#SENT} on success; {@link DeliveryStatus#FAILED} on a
     * permanent error or when the last allowed attempt fails; otherwise still pending, due after the backoff.
     *
     * @throws IllegalStateException if it is no longer pending
     */
    public void recordAttempt(final SendResult result, final Instant now) {
        if (status != DeliveryStatus.PENDING) {
            throw new IllegalStateException("Delivery " + id + " is " + status + ", not PENDING");
        }
        attemptCount++;
        attempts.add(new DeliveryAttempt(
                attemptCount,
                now,
                result.outcome(),
                result.providerMessageId(),
                result.errorCode(),
                result.errorMessage()));
        switch (result.outcome()) {
            case SUCCESS -> {
                status = DeliveryStatus.SENT;
                sentAt = now;
                providerMessageId = result.providerMessageId();
            }
            case PERMANENT_ERROR -> status = DeliveryStatus.FAILED;
            case RETRYABLE_ERROR -> {
                if (attemptCount >= MAX_ATTEMPTS) {
                    status = DeliveryStatus.FAILED;
                } else {
                    nextAttemptAt = now.plus(BACKOFF.get(attemptCount - 1));
                }
            }
        }
    }

    public Long id() {
        return id;
    }

    public long distributionListId() {
        return distributionListId;
    }

    public long memberId() {
        return memberId;
    }

    public Channel channel() {
        return channel;
    }

    public String address() {
        return address;
    }

    public long templateId() {
        return templateId;
    }

    public RenderedMessage content() {
        return content;
    }

    /** When it was accepted, with its notification. */
    public Instant createdAt() {
        return createdAt;
    }

    public DeliveryStatus status() {
        return status;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public Instant nextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant sentAt() {
        return sentAt;
    }

    public String providerMessageId() {
        return providerMessageId;
    }

    /** Oldest first. */
    public List<DeliveryAttempt> attempts() {
        return List.copyOf(attempts);
    }
}
