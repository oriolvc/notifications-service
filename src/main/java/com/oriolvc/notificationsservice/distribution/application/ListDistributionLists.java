package com.oriolvc.notificationsservice.distribution.application;

import com.oriolvc.notificationsservice.distribution.application.model.DistributionListView;
import com.oriolvc.notificationsservice.distribution.application.model.ListDistributionListsCommand;
import com.oriolvc.notificationsservice.distribution.domain.ports.DistributionListRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ListDistributionLists {

    private final DistributionListRepository distributionListRepository;
    private final DistributionListViews views;

    ListDistributionLists(
            final DistributionListRepository distributionListRepository, final DistributionListViews views) {
        this.distributionListRepository = distributionListRepository;
        this.views = views;
    }

    /** All lists of the tenant, active and inactive, oldest first. */
    public List<DistributionListView> execute(final ListDistributionListsCommand command) {
        return distributionListRepository.findAllByCustomerIdOrderByIdAsc(command.tenantId()).stream()
                .map(views::of)
                .toList();
    }
}
