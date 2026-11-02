package com.oriolvc.notificationsservice.notification.infrastructure.persistence;

import com.oriolvc.notificationsservice.notification.domain.Delivery;
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
                dbo.getAttemptCount(),
                dbo.getNextAttemptAt());
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
