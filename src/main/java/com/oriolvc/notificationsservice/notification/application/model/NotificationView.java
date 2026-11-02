package com.oriolvc.notificationsservice.notification.application.model;

import com.oriolvc.notificationsservice.notification.domain.NotificationOutcome;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** A notification with everything that was sent and tried (FR.1.6, AC-4). */
public record NotificationView(
        long id,
        String type,
        String eventKey,
        long customerId,
        NotificationOutcome outcome,
        JsonNode parameters,
        Instant createdAt,
        String createdBy,
        List<DeliveryView> deliveries) {

    public NotificationView {
        deliveries = List.copyOf(deliveries);
    }
}
