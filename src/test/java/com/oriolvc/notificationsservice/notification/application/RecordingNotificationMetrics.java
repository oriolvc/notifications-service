package com.oriolvc.notificationsservice.notification.application;

import com.oriolvc.notificationsservice.notification.domain.Delivery;
import com.oriolvc.notificationsservice.notification.domain.Notification;
import com.oriolvc.notificationsservice.notification.domain.SendResult;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Records what the use cases report, as readable lines. */
final class RecordingNotificationMetrics implements NotificationMetrics {

    final List<String> recorded = new ArrayList<>();

    @Override
    public void notificationCreated(final String type, final Notification notification) {
        recorded.add("created " + type + " " + notification.outcome() + " to "
                + notification.deliveries().size());
    }

    @Override
    public void customersReached(final String type, final double ratio) {
        recorded.add("reached " + type + " " + ratio);
    }

    @Override
    public SendResult timeSend(final Channel channel, final Supplier<SendResult> send) {
        final SendResult result = send.get();
        recorded.add("sent " + channel + " " + result.outcome());
        return result;
    }

    @Override
    public void deliveryCompleted(final Delivery delivery) {
        recorded.add("completed " + delivery.id() + " " + delivery.status());
    }
}
