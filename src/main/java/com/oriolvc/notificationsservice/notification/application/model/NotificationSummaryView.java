package com.oriolvc.notificationsservice.notification.application.model;

import com.oriolvc.notificationsservice.notification.domain.NotificationOutcome;
import java.time.Instant;

public record NotificationSummaryView(
        long id,
        String type,
        String eventKey,
        long customerId,
        NotificationOutcome outcome,
        Instant createdAt,
        String createdBy) {}
