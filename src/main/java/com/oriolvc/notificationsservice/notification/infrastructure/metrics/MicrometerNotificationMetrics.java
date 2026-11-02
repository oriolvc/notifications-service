package com.oriolvc.notificationsservice.notification.infrastructure.metrics;

import com.oriolvc.notificationsservice.notification.application.NotificationMetrics;
import com.oriolvc.notificationsservice.notification.domain.Delivery;
import com.oriolvc.notificationsservice.notification.domain.DeliveryAttempt;
import com.oriolvc.notificationsservice.notification.domain.DeliveryStatus;
import com.oriolvc.notificationsservice.notification.domain.Notification;
import com.oriolvc.notificationsservice.notification.domain.NotificationOutcome;
import com.oriolvc.notificationsservice.notification.domain.SendResult;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Micrometer meters of RFC 13. Tags only take values from small, fixed sets (types, channels, statuses, error codes),
 * never ids or addresses.
 */
@Component
class MicrometerNotificationMetrics implements NotificationMetrics {

    static final String NO_ERROR = "NONE";

    private final MeterRegistry registry;

    MicrometerNotificationMetrics(final MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void notificationCreated(final String type, final Notification notification) {
        Counter.builder("notifications")
                .description("New notifications, one per customer and event; duplicates are not counted")
                .tag("type", type)
                .tag("outcome", notification.outcome().name())
                .register(registry)
                .increment();
        if (notification.outcome() == NotificationOutcome.ACCEPTED) {
            DistributionSummary.builder("notification.recipients")
                    .description("Recipients per accepted notification: the reach of each alert")
                    .tag("type", type)
                    .register(registry)
                    .record(notification.deliveries().size());
        }
    }

    @Override
    public void customersReached(final String type, final double ratio) {
        DistributionSummary.builder("notification.customers.reached.ratio")
                .description("Per event, the share of newly notified customers that had anyone to notify")
                .tag("type", type)
                .register(registry)
                .record(ratio);
    }

    @Override
    public SendResult timeSend(final Channel channel, final Supplier<SendResult> send) {
        final Timer.Sample sample = Timer.start(registry);
        final SendResult result = send.get();
        sample.stop(Timer.builder("notification.channel.send")
                .description("Time to hand one message to the channel's provider")
                .tag("channel", channel.name())
                .tag("outcome", result.outcome().name())
                .publishPercentileHistogram()
                .register(registry));
        return result;
    }

    @Override
    public void deliveryCompleted(final Delivery delivery) {
        final String channel = delivery.channel().name();
        final String status = delivery.status().name();
        Counter.builder("notification.deliveries")
                .description("Deliveries that ended SENT or FAILED")
                .tag("channel", channel)
                .tag("status", status)
                .tag("error_code", errorCode(delivery))
                .register(registry)
                .increment();
        DistributionSummary.builder("notification.delivery.attempts")
                .description("Attempts a delivery took to end SENT or FAILED")
                .tag("channel", channel)
                .tag("status", status)
                .register(registry)
                .record(delivery.attemptCount());
        if (delivery.status() == DeliveryStatus.SENT) {
            Timer.builder("notification.delivery.latency")
                    .description("From acceptance to the provider accepting the message, retries included")
                    .tag("channel", channel)
                    .publishPercentileHistogram()
                    .maximumExpectedValue(Duration.ofHours(2))
                    .register(registry)
                    .record(Duration.between(delivery.createdAt(), delivery.sentAt()));
        }
    }

    /** The last attempt's code for a failed delivery. */
    private static String errorCode(final Delivery delivery) {
        final List<DeliveryAttempt> attempts = delivery.attempts();
        final String code = attempts.getLast().errorCode();
        return delivery.status() == DeliveryStatus.SENT || code == null ? NO_ERROR : code;
    }
}
