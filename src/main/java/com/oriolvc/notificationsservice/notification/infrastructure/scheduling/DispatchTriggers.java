package com.oriolvc.notificationsservice.notification.infrastructure.scheduling;

import com.oriolvc.notificationsservice.notification.application.DispatchDueDeliveries;
import com.oriolvc.notificationsservice.notification.application.model.NotificationsAccepted;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs the dispatcher (RFC 9): on a fixed delay, which also picks up retries, and right after notifications are
 * accepted, off the request thread. Overlapping runs are safe (row locks are skipped, not shared). Disabled with
 * {@code notifications.dispatch.enabled=false}, so tests dispatch synchronously.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableAsync
@ConditionalOnBooleanProperty(name = "notifications.dispatch.enabled", matchIfMissing = true)
class DispatchTriggers {

    private final DispatchDueDeliveries dispatchDueDeliveries;

    DispatchTriggers(final DispatchDueDeliveries dispatchDueDeliveries) {
        this.dispatchDueDeliveries = dispatchDueDeliveries;
    }

    @Scheduled(fixedDelayString = "${notifications.dispatch.poll-interval:10s}")
    void poll() {
        dispatchDueDeliveries.execute();
    }

    @Async
    @EventListener
    void onNotificationsAccepted(final NotificationsAccepted event) {
        dispatchDueDeliveries.execute();
    }
}
