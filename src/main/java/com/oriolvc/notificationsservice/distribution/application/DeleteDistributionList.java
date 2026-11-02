package com.oriolvc.notificationsservice.distribution.application;

import com.oriolvc.notificationsservice.distribution.application.model.DeleteDistributionListCommand;
import com.oriolvc.notificationsservice.distribution.application.ports.DistributionMetrics;
import com.oriolvc.notificationsservice.distribution.application.ports.DistributionMetrics.ConfigChange;
import com.oriolvc.notificationsservice.distribution.domain.DistributionList;
import com.oriolvc.notificationsservice.distribution.domain.exceptions.DistributionListNotFoundException;
import com.oriolvc.notificationsservice.distribution.domain.ports.DistributionListRepository;
import org.springframework.stereotype.Service;

/** Soft delete (D-5). Idempotent: deleting an inactive list changes nothing. */
@Service
public class DeleteDistributionList {

    private final DistributionListRepository distributionListRepository;
    private final DistributionMetrics metrics;

    DeleteDistributionList(
            final DistributionListRepository distributionListRepository, final DistributionMetrics metrics) {
        this.distributionListRepository = distributionListRepository;
        this.metrics = metrics;
    }

    public void execute(final DeleteDistributionListCommand command) {
        final DistributionList distributionList = distributionListRepository
                .findByIdAndCustomerId(command.listId(), command.tenantId())
                .orElseThrow(() -> new DistributionListNotFoundException(command.listId()));
        if (distributionList.isActive()) {
            distributionList.deactivate();
            distributionListRepository.save(distributionList);
            metrics.configChanged(ConfigChange.LIST_DEACTIVATED);
        }
    }
}
