package com.oriolvc.notificationsservice.notification.domain;

import java.time.Instant;
import java.util.function.Consumer;

/** The {@code PENDING} deliveries are the dispatcher's work queue (RFC 9). */
public interface DeliveryRepository {

    /**
     * In one transaction: locks the {@code PENDING} delivery due longest ago (skipping deliveries other dispatchers
     * hold), hands it to {@code attempt}, and stores its new state and attempts. If {@code attempt} throws, nothing is
     * stored and the delivery stays due.
     *
     * @return whether a delivery was due
     */
    boolean attemptNextDue(Instant now, Consumer<Delivery> attempt);
}
