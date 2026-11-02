package com.oriolvc.notificationsservice.notification.infrastructure.web;

import com.oriolvc.notificationsservice.generated.model.CreateNotificationRequest;
import com.oriolvc.notificationsservice.generated.model.CreateNotificationResponse;
import com.oriolvc.notificationsservice.generated.model.CustomerOutcome;
import com.oriolvc.notificationsservice.generated.model.CustomerResult;
import com.oriolvc.notificationsservice.notification.application.model.CreateNotificationCommand;
import com.oriolvc.notificationsservice.notification.application.model.CreateNotificationResult;
import java.util.List;

/** Generated DTOs ↔ application types. Generated code never leaves this package. */
final class NotificationWebMapper {

    private NotificationWebMapper() {}

    /** The generic command: the parameters stay JSON, validated by the type's schema only (RFC 6.3). */
    static CreateNotificationCommand toCommand(final CreateNotificationRequest request) {
        return new CreateNotificationCommand(
                request.getType().getValue(), List.copyOf(request.getCustomerIds()), request.getParameters());
    }

    static CreateNotificationResponse toDto(final CreateNotificationResult result) {
        return new CreateNotificationResponse()
                .eventKey(result.eventKey())
                .results(result.results().stream()
                        .map(customer -> new CustomerResult()
                                .customerId(customer.customerId())
                                .notificationId(customer.notificationId())
                                .outcome(CustomerOutcome.fromValue(
                                        customer.outcome().name()))
                                .deliveries(customer.deliveries()))
                        .toList());
    }
}
