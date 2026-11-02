package com.oriolvc.notificationsservice.notification.infrastructure.persistence;

import com.oriolvc.notificationsservice.notification.domain.Delivery;
import com.oriolvc.notificationsservice.notification.domain.DeliveryAttempt;
import com.oriolvc.notificationsservice.notification.domain.EventKey;
import com.oriolvc.notificationsservice.notification.domain.Notification;
import com.oriolvc.notificationsservice.notification.domain.RenderedMessage;
import tools.jackson.databind.json.JsonMapper;

final class NotificationDboMapper {

    private NotificationDboMapper() {}

    static Notification toDomain(final NotificationDbo dbo) {
        return Notification.restore(
                dbo.getId(),
                dbo.getNotificationTypeId(),
                new EventKey(dbo.getEventKey()),
                dbo.getCustomerId(),
                JsonMapper.shared().readTree(dbo.getParameters()),
                dbo.getOutcome(),
                dbo.getCreatedAt(),
                dbo.getCreatedBy(),
                dbo.getDeliveries().stream()
                        .map(NotificationDboMapper::toDomain)
                        .toList());
    }

    static Delivery toDomain(final DeliveryDbo dbo) {
        return Delivery.restore(
                dbo.getId(),
                dbo.getDistributionListId(),
                dbo.getMemberId(),
                dbo.getChannel(),
                dbo.getAddress(),
                dbo.getTemplateId(),
                new RenderedMessage(dbo.getSubject(), dbo.getBody()),
                dbo.getStatus(),
                dbo.getNextAttemptAt(),
                dbo.getSentAt(),
                dbo.getProviderMessageId(),
                dbo.getAttempts().stream()
                        .map(attempt -> new DeliveryAttempt(
                                attempt.getAttemptNumber(),
                                attempt.getAttemptedAt(),
                                attempt.getOutcome(),
                                attempt.getProviderMessageId(),
                                attempt.getErrorCode(),
                                attempt.getErrorMessage()))
                        .toList());
    }

    static DeliveryAttemptDbo toDbo(final DeliveryAttempt attempt, final DeliveryDbo delivery) {
        return new DeliveryAttemptDbo(
                delivery,
                attempt.attemptNumber(),
                attempt.attemptedAt(),
                attempt.outcome(),
                attempt.providerMessageId(),
                attempt.errorCode(),
                attempt.errorMessage());
    }

    static DeliveryDbo toDbo(final Delivery delivery, final NotificationDbo notification) {
        return new DeliveryDbo(
                notification,
                delivery.distributionListId(),
                delivery.memberId(),
                delivery.channel(),
                delivery.address(),
                delivery.templateId(),
                delivery.content().subject(),
                delivery.content().body(),
                delivery.status(),
                delivery.attemptCount(),
                delivery.nextAttemptAt());
    }
}
