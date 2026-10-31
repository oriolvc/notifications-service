package com.oriolvc.notificationsservice.distribution.application;

import com.oriolvc.notificationsservice.distribution.domain.DistributionListNotFoundException;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetDistributionList {

    private final DistributionListRepository lists;
    private final DistributionListViews views;

    GetDistributionList(DistributionListRepository lists, DistributionListViews views) {
        this.lists = lists;
        this.views = views;
    }

    @Transactional(readOnly = true)
    public DistributionListView execute(GetDistributionListCommand command) {
        return lists.findByIdAndCustomerId(command.listId(), command.tenantId())
                .map(views::of)
                .orElseThrow(() -> new DistributionListNotFoundException(command.listId()));
    }
}
