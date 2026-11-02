package com.oriolvc.notificationsservice.notification.domain;

import java.time.Instant;

/** A stored notification without its deliveries, for searches. */
public record NotificationSummary(
        long id,
        long notificationTypeId,
        EventKey eventKey,
        long customerId,
        NotificationOutcome outcome,
        Instant createdAt,
        String createdBy) {}
