package com.oriolvc.notificationsservice.notification.infrastructure.metrics;

import com.oriolvc.notificationsservice.notification.domain.DeliveryRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * The dispatcher's backlog, read from the database at each scrape (RFC 13). The values are database-wide, so every
 * instance reports the same: aggregate them with {@code max}, not {@code sum}.
 */
@Component
class DeliveryBacklogMeters implements MeterBinder {

    private final DeliveryRepository deliveryRepository;
    private final Clock clock;

    DeliveryBacklogMeters(final DeliveryRepository deliveryRepository, final Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.clock = clock;
    }

    @Override
    public void bindTo(final MeterRegistry registry) {
        Gauge.builder("notification.deliveries.pending", deliveryRepository, DeliveryRepository::countPending)
                .description("Deliveries waiting to be sent or retried")
                .register(registry);
        Gauge.builder("notification.deliveries.pending.oldest.age", this, DeliveryBacklogMeters::oldestPendingAge)
                .description("How long the oldest pending delivery has been waiting since it was accepted")
                .baseUnit("seconds")
                .register(registry);
    }

    /** In seconds; 0 with nothing pending. */
    double oldestPendingAge() {
        return deliveryRepository
                .oldestPendingCreatedAt()
                .map(createdAt -> Duration.between(createdAt, clock.instant()).toMillis())
                .map(millis -> millis / (double) TimeUnit.SECONDS.toMillis(1))
                .orElse(0.0);
    }
}
