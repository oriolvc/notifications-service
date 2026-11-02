package com.oriolvc.notificationsservice.distribution.infrastructure.metrics;

import com.oriolvc.notificationsservice.distribution.application.ports.DistributionMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
class MicrometerDistributionMetrics implements DistributionMetrics {

    private final MeterRegistry registry;

    MicrometerDistributionMetrics(final MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void configChanged(final ConfigChange change) {
        Counter.builder("distribution.config.changes")
                .description("Changes customers made to their distribution lists and members")
                .tag("operation", change.name())
                .register(registry)
                .increment();
    }
}
