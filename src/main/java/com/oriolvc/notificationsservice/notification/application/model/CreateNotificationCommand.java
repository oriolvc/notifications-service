package com.oriolvc.notificationsservice.notification.application.model;

import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * @param type the notification type code
 * @param customerIds the affected customers; a customer listed twice is processed once
 * @param parameters as received: normalized and validated by the use case
 */
public record CreateNotificationCommand(String type, List<Long> customerIds, JsonNode parameters) {

    public CreateNotificationCommand {
        customerIds = List.copyOf(customerIds);
    }
}
