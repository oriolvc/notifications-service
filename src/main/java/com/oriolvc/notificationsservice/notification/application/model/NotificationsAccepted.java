package com.oriolvc.notificationsservice.notification.application.model;

import java.util.List;

/**
 * Published once new notifications with deliveries are committed, so the dispatcher can send them right away rather
 * than at its next poll (RFC 9).
 */
public record NotificationsAccepted(List<Long> notificationIds) {

    public NotificationsAccepted {
        notificationIds = List.copyOf(notificationIds);
    }
}
