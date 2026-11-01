package com.oriolvc.notificationsservice.distribution.application;

import com.oriolvc.notificationsservice.distribution.application.model.DistributionListView;
import com.oriolvc.notificationsservice.distribution.application.model.GetDistributionListCommand;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListNotFoundException;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListRepository;
import org.springframework.stereotype.Service;

@Service
public class GetDistributionList {

    private final DistributionListRepository distributionListRepository;
    private final DistributionListViews views;

    GetDistributionList(DistributionListRepository distributionListRepository, DistributionListViews views) {
        this.distributionListRepository = distributionListRepository;
        this.views = views;
    }

    public DistributionListView execute(GetDistributionListCommand command) {
        return distributionListRepository
                .findByIdAndCustomerId(command.listId(), command.tenantId())
                .map(views::of)
                .orElseThrow(() -> new DistributionListNotFoundException(command.listId()));
    }
}
