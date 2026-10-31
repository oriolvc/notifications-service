package com.oriolvc.notificationsservice.distribution.application;

import com.oriolvc.notificationsservice.distribution.domain.DistributionList;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListNotFoundException;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateDistributionList {

    private final DistributionListRepository lists;
    private final DistributionListViews views;

    UpdateDistributionList(DistributionListRepository lists, DistributionListViews views) {
        this.lists = lists;
        this.views = views;
    }

    @Transactional
    public DistributionListView execute(UpdateDistributionListCommand command) {
        DistributionList list = lists.findByIdAndCustomerId(command.listId(), command.tenantId())
                .orElseThrow(() -> new DistributionListNotFoundException(command.listId()));
        list.checkVersion(command.version());

        // A taken name or reactivating next to another active list is rejected by the unique constraints (409)
        String name = DistributionList.normalizeName(command.name());
        if (name != null) {
            list.rename(name);
        }
        if (Boolean.TRUE.equals(command.active())) {
            list.activate();
        } else if (Boolean.FALSE.equals(command.active())) {
            list.deactivate();
        }
        return views.of(lists.save(list));
    }
}
