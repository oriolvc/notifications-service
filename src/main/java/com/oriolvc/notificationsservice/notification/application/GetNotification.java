package com.oriolvc.notificationsservice.notification.application;

import com.oriolvc.notificationsservice.notification.application.model.DeliveryView;
import com.oriolvc.notificationsservice.notification.application.model.GetNotificationCommand;
import com.oriolvc.notificationsservice.notification.application.model.NotificationView;
import com.oriolvc.notificationsservice.notification.domain.Delivery;
import com.oriolvc.notificationsservice.notification.domain.DeliveryStatus;
import com.oriolvc.notificationsservice.notification.domain.Notification;
import com.oriolvc.notificationsservice.notification.domain.NotificationNotFoundException;
import com.oriolvc.notificationsservice.notification.domain.NotificationRepository;
import com.oriolvc.notificationsservice.notification.domain.NotificationType;
import com.oriolvc.notificationsservice.notification.domain.NotificationTypeRepository;
import org.springframework.stereotype.Service;

/** Tracking (RFC 6.1, AC-4). Another tenant's notification is reported as not found. */
@Service
public class GetNotification {

    private final NotificationRepository notificationRepository;
    private final NotificationTypeRepository notificationTypeRepository;

    GetNotification(
            final NotificationRepository notificationRepository,
            final NotificationTypeRepository notificationTypeRepository) {
        this.notificationRepository = notificationRepository;
        this.notificationTypeRepository = notificationTypeRepository;
    }

    public NotificationView execute(final GetNotificationCommand command) {
        final Notification notification = notificationRepository
                .findById(command.notificationId())
                .filter(found -> command.tenantId() == null || found.customerId() == command.tenantId())
                .orElseThrow(() -> new NotificationNotFoundException(command.notificationId()));
        final String type = notificationTypeRepository
                .findById(notification.notificationTypeId())
                .map(NotificationType::code)
                .orElseThrow();
        return new NotificationView(
                notification.id(),
                type,
                notification.eventKey().value(),
                notification.customerId(),
                notification.outcome(),
                notification.parameters(),
                notification.createdAt(),
                notification.createdBy(),
                notification.deliveries().stream().map(GetNotification::toView).toList());
    }

    private static DeliveryView toView(final Delivery delivery) {
        return new DeliveryView(
                delivery.id(),
                delivery.distributionListId(),
                delivery.memberId(),
                delivery.channel(),
                delivery.address(),
                delivery.content().subject(),
                delivery.content().body(),
                delivery.status(),
                delivery.attemptCount(),
                delivery.status() == DeliveryStatus.PENDING ? delivery.nextAttemptAt() : null,
                delivery.sentAt(),
                delivery.providerMessageId(),
                delivery.attempts());
    }
}
