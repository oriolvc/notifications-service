package com.oriolvc.notificationsservice.distribution.application;

import com.oriolvc.notificationsservice.distribution.domain.DistributionList;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListNotFoundException;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListRepository;
import org.springframework.stereotype.Service;

@Service
public class UpdateDistributionList {

    private final DistributionListRepository distributionListRepository;
    private final DistributionListViews views;

    UpdateDistributionList(DistributionListRepository distributionListRepository, DistributionListViews views) {
        this.distributionListRepository = distributionListRepository;
        this.views = views;
    }

    public DistributionListView execute(UpdateDistributionListCommand command) {
        DistributionList distributionList = distributionListRepository
                .findByIdAndCustomerId(command.listId(), command.tenantId())
                .orElseThrow(() -> new DistributionListNotFoundException(command.listId()));
        distributionList.checkVersion(command.version());

        // A taken name or reactivating next to another active list is rejected by the unique constraints (409)
        String name = DistributionList.normalizeName(command.name());
        if (name != null) {
            distributionList.rename(name);
        }
        if (Boolean.TRUE.equals(command.active())) {
            distributionList.activate();
        } else if (Boolean.FALSE.equals(command.active())) {
            distributionList.deactivate();
        }
        return views.of(distributionListRepository.save(distributionList));
    }
}
