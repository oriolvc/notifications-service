package com.oriolvc.notificationsservice.notification.domain;

import com.oriolvc.notificationsservice.notification.domain.ports.NotificationRepository;

/**
 * A notification as stored by {@link NotificationRepository#saveAllIfAbsent}.
 *
 * @param notification the stored notification: the new one, or for a duplicate the one stored earlier
 * @param duplicate whether the same event had already been stored for the customer, so nothing new was stored
 */
public record SavedNotification(Notification notification, boolean duplicate) {}
