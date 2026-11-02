package com.oriolvc.notificationsservice.distribution.application;

import com.oriolvc.notificationsservice.distribution.application.DistributionMetrics.ConfigChange;
import com.oriolvc.notificationsservice.distribution.application.model.DistributionListView;
import com.oriolvc.notificationsservice.distribution.application.model.UpdateDistributionListCommand;
import com.oriolvc.notificationsservice.distribution.domain.DistributionList;
import com.oriolvc.notificationsservice.distribution.domain.exceptions.DistributionListNotFoundException;
import com.oriolvc.notificationsservice.distribution.domain.ports.DistributionListRepository;
import org.springframework.stereotype.Service;

@Service
public class UpdateDistributionList {

    private final DistributionListRepository distributionListRepository;
    private final DistributionMetrics metrics;
    private final DistributionListViews views;

    UpdateDistributionList(
            final DistributionListRepository distributionListRepository,
            final DistributionListViews views,
            final DistributionMetrics metrics) {
        this.distributionListRepository = distributionListRepository;
        this.metrics = metrics;
        this.views = views;
    }

    public DistributionListView execute(final UpdateDistributionListCommand command) {
        final DistributionList distributionList = distributionListRepository
                .findByIdAndCustomerId(command.listId(), command.tenantId())
                .orElseThrow(() -> new DistributionListNotFoundException(command.listId()));
        distributionList.checkVersion(command.version());

        // A taken name or reactivating next to another active list is rejected by the unique constraints (409)
        final String name = DistributionList.normalizeName(command.name());
        if (name != null) {
            distributionList.rename(name);
        }
        if (Boolean.TRUE.equals(command.active())) {
            distributionList.activate();
        } else if (Boolean.FALSE.equals(command.active())) {
            distributionList.deactivate();
        }
        final DistributionList saved = distributionListRepository.save(distributionList);
        if (saved.version() != distributionList.version()) {
            metrics.configChanged(ConfigChange.LIST_UPDATED);
        }
        return views.of(saved);
    }
}
