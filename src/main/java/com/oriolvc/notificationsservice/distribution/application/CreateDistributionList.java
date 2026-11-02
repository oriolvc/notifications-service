package com.oriolvc.notificationsservice.distribution.application;

import com.oriolvc.notificationsservice.distribution.application.DistributionMetrics.ConfigChange;
import com.oriolvc.notificationsservice.distribution.application.model.CreateDistributionListCommand;
import com.oriolvc.notificationsservice.distribution.application.model.DistributionListView;
import com.oriolvc.notificationsservice.distribution.domain.DistributionList;
import com.oriolvc.notificationsservice.distribution.domain.ports.DistributionListRepository;
import com.oriolvc.notificationsservice.notification.application.NotificationTypeQuery;
import com.oriolvc.notificationsservice.notification.application.model.NotificationTypeRef;
import com.oriolvc.notificationsservice.shared.error.UnprocessableException;
import com.oriolvc.notificationsservice.shared.error.Violation;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CreateDistributionList {

    private final DistributionListRepository distributionListRepository;
    private final DistributionMetrics metrics;
    private final NotificationTypeQuery notificationTypes;
    private final DistributionListViews views;

    CreateDistributionList(
            final DistributionListRepository distributionListRepository,
            final NotificationTypeQuery notificationTypes,
            final DistributionListViews views,
            final DistributionMetrics metrics) {
        this.distributionListRepository = distributionListRepository;
        this.metrics = metrics;
        this.notificationTypes = notificationTypes;
        this.views = views;
    }

    public DistributionListView execute(final CreateDistributionListCommand command) {
        final NotificationTypeRef type = notificationTypes
                .findActiveByCode(command.notificationType())
                .orElseThrow(() -> new UnprocessableException(
                        "Unknown or inactive notification type",
                        List.of(new Violation("/notificationType", "unknown or inactive notification type"))));
        // Duplicate names and a second active list are rejected by the unique constraints (409)
        final DistributionList distributionList = DistributionList.create(
                command.tenantId(), DistributionList.normalizeName(command.name()), type.id(), command.channel());
        final DistributionList saved = distributionListRepository.save(distributionList);
        metrics.configChanged(ConfigChange.LIST_CREATED);
        return views.of(saved);
    }
}
