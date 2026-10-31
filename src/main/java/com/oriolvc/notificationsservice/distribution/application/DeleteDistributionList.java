package com.oriolvc.notificationsservice.distribution.application;

import com.oriolvc.notificationsservice.distribution.application.model.DeleteDistributionListCommand;
import com.oriolvc.notificationsservice.distribution.domain.DistributionList;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListNotFoundException;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListRepository;
import org.springframework.stereotype.Service;

/** Soft delete (D-5). Idempotent: deleting an inactive list changes nothing. */
@Service
public class DeleteDistributionList {

    private final DistributionListRepository distributionListRepository;

    DeleteDistributionList(final DistributionListRepository distributionListRepository) {
        this.distributionListRepository = distributionListRepository;
    }

    public void execute(final DeleteDistributionListCommand command) {
        final DistributionList distributionList = distributionListRepository
                .findByIdAndCustomerId(command.listId(), command.tenantId())
                .orElseThrow(() -> new DistributionListNotFoundException(command.listId()));
        if (distributionList.isActive()) {
            distributionList.deactivate();
            distributionListRepository.save(distributionList);
        }
    }
}
