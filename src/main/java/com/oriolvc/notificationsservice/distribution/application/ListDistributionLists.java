package com.oriolvc.notificationsservice.distribution.application;

import com.oriolvc.notificationsservice.distribution.domain.DistributionListRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ListDistributionLists {

    private final DistributionListRepository lists;
    private final DistributionListViews views;

    ListDistributionLists(DistributionListRepository lists, DistributionListViews views) {
        this.lists = lists;
        this.views = views;
    }

    /** All lists of the tenant, active and inactive, oldest first. */
    public List<DistributionListView> execute(ListDistributionListsCommand command) {
        return lists.findAllByCustomerIdOrderByIdAsc(command.tenantId()).stream()
                .map(views::of)
                .toList();
    }
}
