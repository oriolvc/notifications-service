package com.oriolvc.notificationsservice.distribution.application;

import com.oriolvc.notificationsservice.distribution.application.model.DistributionListView;
import com.oriolvc.notificationsservice.distribution.application.model.GetDistributionListCommand;
import com.oriolvc.notificationsservice.distribution.domain.exceptions.DistributionListNotFoundException;
import com.oriolvc.notificationsservice.distribution.domain.ports.DistributionListRepository;
import org.springframework.stereotype.Service;

@Service
public class GetDistributionList {

    private final DistributionListRepository distributionListRepository;
    private final DistributionListViews views;

    GetDistributionList(
            final DistributionListRepository distributionListRepository, final DistributionListViews views) {
        this.distributionListRepository = distributionListRepository;
        this.views = views;
    }

    public DistributionListView execute(final GetDistributionListCommand command) {
        return distributionListRepository
                .findByIdAndCustomerId(command.listId(), command.tenantId())
                .map(views::of)
                .orElseThrow(() -> new DistributionListNotFoundException(command.listId()));
    }
}
