package com.oriolvc.notificationsservice.distribution.infrastructure.metrics;

import com.oriolvc.notificationsservice.distribution.domain.ports.DistributionListRepository;
import com.oriolvc.notificationsservice.notification.application.NotificationTypeQuery;
import com.oriolvc.notificationsservice.notification.application.model.NotificationTypeRef;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * Active lists per notification type and channel, counted in the database at each scrape (RFC 13.1). Types come from
 * Liquibase, so the ones present at startup are all there are. The values are database-wide: aggregate instances with
 * {@code max}.
 */
@Component
class ActiveListMeters implements MeterBinder {

    private final DistributionListRepository distributionListRepository;
    private final NotificationTypeQuery notificationTypes;

    ActiveListMeters(
            final DistributionListRepository distributionListRepository,
            final NotificationTypeQuery notificationTypes) {
        this.distributionListRepository = distributionListRepository;
        this.notificationTypes = notificationTypes;
    }

    @Override
    public void bindTo(final MeterRegistry registry) {
        for (final NotificationTypeRef type : notificationTypes.findAll()) {
            for (final Channel channel : Channel.values()) {
                Gauge.builder(
                                "distribution.lists.active",
                                () -> distributionListRepository.countActive(type.id(), channel))
                        .description("Active distribution lists: customers set up to receive this type")
                        .tag("type", type.code())
                        .tag("channel", channel.name())
                        .register(registry);
            }
        }
    }
}
