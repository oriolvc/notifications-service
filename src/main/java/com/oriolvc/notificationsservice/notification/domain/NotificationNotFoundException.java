package com.oriolvc.notificationsservice.notification.domain;

import com.oriolvc.notificationsservice.shared.error.NotFoundException;

/** Also thrown for another tenant's notification, so its existence is not revealed. */
public class NotificationNotFoundException extends NotFoundException {

    public NotificationNotFoundException(final long notificationId) {
        super("Notification " + notificationId + " not found");
    }
}
