package com.oriolvc.notificationsservice.notification.application;

import com.oriolvc.notificationsservice.notification.domain.Delivery;
import com.oriolvc.notificationsservice.notification.domain.DeliveryStatus;
import com.oriolvc.notificationsservice.notification.domain.SendResult;
import com.oriolvc.notificationsservice.notification.domain.ports.DeliveryRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends every delivery that is due, one at a time, each in its own transaction holding the delivery's row lock (RFC 9).
 * Safe to run concurrently, here or on other instances: a locked delivery is skipped, not waited for. Triggered by the
 * scheduler and after each accepted notification; tests call it directly.
 */
@Service
public class DispatchDueDeliveries {

    private static final Logger log = LoggerFactory.getLogger(DispatchDueDeliveries.class);

    private final DeliveryRepository deliveryRepository;
    private final ChannelSenders senders;
    private final NotificationMetrics metrics;
    private final Clock clock;

    DispatchDueDeliveries(
            final DeliveryRepository deliveryRepository,
            final ChannelSenders senders,
            final NotificationMetrics metrics,
            final Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.senders = senders;
        this.metrics = metrics;
        this.clock = clock;
    }

    /** @return how many deliveries were attempted */
    public int execute() {
        int attempted = 0;
        try {
            while (deliveryRepository.attemptNextDue(clock.instant(), this::attempt)) {
                attempted++;
            }
        } catch (final RuntimeException e) {
            // The delivery stays due; stop here rather than pick the same one again right away
            log.error("Dispatch stopped after {} deliveries", attempted, e);
        }
        return attempted;
    }

    private void attempt(final Delivery delivery) {
        final ChannelSender sender = senders.forChannel(delivery.channel());
        final SendResult result = metrics.timeSend(delivery.channel(), () -> sender.send(delivery));
        delivery.recordAttempt(result, clock.instant());
        if (delivery.status() != DeliveryStatus.PENDING) {
            metrics.deliveryCompleted(delivery);
        }
        log.info(
                "Delivery {} attempt {}: {} {}, now {}",
                delivery.id(),
                delivery.attemptCount(),
                result.outcome(),
                result.errorCode() == null ? "" : result.errorCode(),
                delivery.status());
    }
}
