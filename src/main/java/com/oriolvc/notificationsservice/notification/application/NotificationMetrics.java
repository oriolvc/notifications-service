package com.oriolvc.notificationsservice.notification.application;

import com.oriolvc.notificationsservice.notification.domain.Delivery;
import com.oriolvc.notificationsservice.notification.domain.Notification;
import com.oriolvc.notificationsservice.notification.domain.SendResult;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import java.util.function.Supplier;

/** The adoption and performance metrics recorded as notifications are created and sent (RFC 13). */
public interface NotificationMetrics {

    /** A new notification, not a duplicate, with how many recipients its customer had. */
    void notificationCreated(String type, Notification notification);

    /** For one event: the share of its newly notified customers that had anyone to notify. */
    void customersReached(String type, double ratio);

    /** Sends through a channel, timing it. */
    SendResult timeSend(Channel channel, Supplier<SendResult> send);

    /** A delivery that has just been {@code SENT} or {@code FAILED}. */
    void deliveryCompleted(Delivery delivery);
}
