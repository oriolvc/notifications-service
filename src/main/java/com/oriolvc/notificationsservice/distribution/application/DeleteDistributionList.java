package com.oriolvc.notificationsservice.distribution.application;

import com.oriolvc.notificationsservice.distribution.domain.DistributionList;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListNotFoundException;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Soft delete (D-5). Idempotent: deleting an inactive list changes nothing. */
@Service
public class DeleteDistributionList {

    private final DistributionListRepository lists;

    DeleteDistributionList(DistributionListRepository lists) {
        this.lists = lists;
    }

    @Transactional
    public void execute(DeleteDistributionListCommand command) {
        DistributionList list = lists.findByIdAndCustomerId(command.listId(), command.tenantId())
                .orElseThrow(() -> new DistributionListNotFoundException(command.listId()));
        if (list.isActive()) {
            list.deactivate();
            lists.save(list);
        }
    }
}
