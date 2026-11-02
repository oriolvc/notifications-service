package com.oriolvc.notificationsservice.notification.domain;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository {

    /**
     * Stores the notifications with their deliveries, all or nothing. A notification whose (type, event key, customer)
     * is already stored is skipped and the stored one is returned instead; the unique constraint decides, so this
     * holds under concurrent calls (AC-9).
     *
     * @return one result per notification, in the same order
     */
    List<SavedNotification> saveAllIfAbsent(List<Notification> notifications);

    /** With its deliveries and their attempts. */
    Optional<Notification> findById(long id);

    /**
     * Newest first.
     *
     * @param customerId only this customer's notifications; {@code null} for every customer
     * @param eventKey only this event's notifications; {@code null} for every event
     * @param limit at most this many
     */
    List<NotificationSummary> search(Long customerId, EventKey eventKey, int limit);
}
