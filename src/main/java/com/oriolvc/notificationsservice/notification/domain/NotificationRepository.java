package com.oriolvc.notificationsservice.notification.domain;

import java.util.List;

public interface NotificationRepository {

    /**
     * Stores the notifications with their deliveries, all or nothing. A notification whose (type, event key, customer)
     * is already stored is skipped and the stored one is returned instead; the unique constraint decides, so this
     * holds under concurrent calls (AC-9).
     *
     * @return one result per notification, in the same order
     */
    List<SavedNotification> saveAllIfAbsent(List<Notification> notifications);
}
