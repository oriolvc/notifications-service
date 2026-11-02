package com.oriolvc.notificationsservice.notification.infrastructure.web;

import com.oriolvc.notificationsservice.generated.model.AttemptOutcome;
import com.oriolvc.notificationsservice.generated.model.Channel;
import com.oriolvc.notificationsservice.generated.model.CreateNotificationRequest;
import com.oriolvc.notificationsservice.generated.model.CreateNotificationResponse;
import com.oriolvc.notificationsservice.generated.model.CustomerOutcome;
import com.oriolvc.notificationsservice.generated.model.CustomerResult;
import com.oriolvc.notificationsservice.generated.model.Delivery;
import com.oriolvc.notificationsservice.generated.model.DeliveryAttempt;
import com.oriolvc.notificationsservice.generated.model.DeliveryStatus;
import com.oriolvc.notificationsservice.generated.model.NotificationDetail;
import com.oriolvc.notificationsservice.generated.model.NotificationOutcome;
import com.oriolvc.notificationsservice.generated.model.NotificationSearchResult;
import com.oriolvc.notificationsservice.generated.model.NotificationSummary;
import com.oriolvc.notificationsservice.notification.application.model.CreateNotificationCommand;
import com.oriolvc.notificationsservice.notification.application.model.CreateNotificationResult;
import com.oriolvc.notificationsservice.notification.application.model.DeliveryView;
import com.oriolvc.notificationsservice.notification.application.model.NotificationSummaryView;
import com.oriolvc.notificationsservice.notification.application.model.NotificationView;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

    static NotificationDetail toDto(final NotificationView view) {
        return new NotificationDetail()
                .id(view.id())
                .type(view.type())
                .eventKey(view.eventKey())
                .customerId(view.customerId())
                .outcome(NotificationOutcome.fromValue(view.outcome().name()))
                .parameters(view.parameters())
                .createdAt(toOffset(view.createdAt()))
                .createdBy(view.createdBy())
                .deliveries(view.deliveries().stream()
                        .map(NotificationWebMapper::toDto)
                        .toList());
    }

    static NotificationSearchResult toDto(final List<NotificationSummaryView> views) {
        return new NotificationSearchResult()
                .items(views.stream()
                        .map(view -> new NotificationSummary()
                                .id(view.id())
                                .type(view.type())
                                .eventKey(view.eventKey())
                                .customerId(view.customerId())
                                .outcome(NotificationOutcome.fromValue(
                                        view.outcome().name()))
                                .createdAt(toOffset(view.createdAt()))
                                .createdBy(view.createdBy()))
                        .toList());
    }

    private static Delivery toDto(final DeliveryView view) {
        return new Delivery()
                .id(view.id())
                .distributionListId(view.distributionListId())
                .memberId(view.memberId())
                .channel(Channel.fromValue(view.channel().name()))
                .address(view.address())
                .subject(view.subject())
                .body(view.body())
                .status(DeliveryStatus.fromValue(view.status().name()))
                .attemptCount(view.attemptCount())
                .nextAttemptAt(toOffset(view.nextAttemptAt()))
                .sentAt(toOffset(view.sentAt()))
                .providerMessageId(view.providerMessageId())
                .attempts(view.attempts().stream()
                        .map(attempt -> new DeliveryAttempt()
                                .attemptNumber(attempt.attemptNumber())
                                .attemptedAt(toOffset(attempt.attemptedAt()))
                                .outcome(AttemptOutcome.fromValue(
                                        attempt.outcome().name()))
                                .providerMessageId(attempt.providerMessageId())
                                .errorCode(attempt.errorCode())
                                .errorMessage(attempt.errorMessage()))
                        .toList());
    }

    private static OffsetDateTime toOffset(final Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
