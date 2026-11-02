package com.oriolvc.notificationsservice.notification.domain;

import com.oriolvc.notificationsservice.shared.domain.Channel;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.JsonNode;

/**
 * One event for one customer, with a delivery per recipient. Immutable once stored: deliveries change status, the
 * notification never does. At most one per (type, event key, customer), enforced by a unique constraint (RFC 7.3).
 */
public final class Notification {

    private final Long id;
    private final long notificationTypeId;
    private final EventKey eventKey;
    private final long customerId;
    private final JsonNode parameters;
    private final NotificationOutcome outcome;
    private final Instant createdAt;
    private final String createdBy;
    private final List<Delivery> deliveries;

    private Notification(
            final Long id,
            final long notificationTypeId,
            final EventKey eventKey,
            final long customerId,
            final JsonNode parameters,
            final NotificationOutcome outcome,
            final Instant createdAt,
            final String createdBy,
            final List<Delivery> deliveries) {
        this.id = id;
        this.notificationTypeId = notificationTypeId;
        this.eventKey = Objects.requireNonNull(eventKey, "eventKey");
        this.customerId = customerId;
        this.parameters = Objects.requireNonNull(parameters, "parameters");
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.deliveries = List.copyOf(deliveries);
    }

    /**
     * Renders the type's template once per channel and creates a {@link DeliveryStatus#PENDING} delivery per
     * recipient, due {@code now}. No recipients → {@link NotificationOutcome#NO_RECIPIENTS}, still stored.
     *
     * @param parameters normalized and valid for the type's schema
     * @throws IllegalStateException if a recipient's channel has no template for the type
     */
    public static Notification create(
            final NotificationType type,
            final EventKey eventKey,
            final long customerId,
            final JsonNode parameters,
            final List<Recipient> recipients,
            final Instant now) {
        final Map<Channel, RenderedMessage> rendered = new EnumMap<>(Channel.class);
        final List<Delivery> deliveries = recipients.stream()
                .map(recipient -> {
                    final Template template = type.template(recipient.channel())
                            .orElseThrow(() -> new IllegalStateException(
                                    type.code() + " has no " + recipient.channel() + " template"));
                    final RenderedMessage content = rendered.computeIfAbsent(
                            recipient.channel(),
                            channel -> TemplateRenderer.render(template, type.parametersSchema(), parameters));
                    return Delivery.pending(recipient, template, content, now);
                })
                .toList();
        return new Notification(
                null,
                type.id(),
                eventKey,
                customerId,
                parameters,
                deliveries.isEmpty() ? NotificationOutcome.NO_RECIPIENTS : NotificationOutcome.ACCEPTED,
                null,
                null,
                deliveries);
    }

    /** Rebuilds a stored notification. */
    public static Notification restore(
            final long id,
            final long notificationTypeId,
            final EventKey eventKey,
            final long customerId,
            final JsonNode parameters,
            final NotificationOutcome outcome,
            final Instant createdAt,
            final String createdBy,
            final List<Delivery> deliveries) {
        return new Notification(
                id, notificationTypeId, eventKey, customerId, parameters, outcome, createdAt, createdBy, deliveries);
    }

    public Long id() {
        return id;
    }

    public long notificationTypeId() {
        return notificationTypeId;
    }

    public EventKey eventKey() {
        return eventKey;
    }

    public long customerId() {
        return customerId;
    }

    /** The normalized parameters. */
    public JsonNode parameters() {
        return parameters;
    }

    public NotificationOutcome outcome() {
        return outcome;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String createdBy() {
        return createdBy;
    }

    public List<Delivery> deliveries() {
        return deliveries;
    }
}
