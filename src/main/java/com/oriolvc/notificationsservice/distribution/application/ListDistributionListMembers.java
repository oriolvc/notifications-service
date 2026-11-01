package com.oriolvc.notificationsservice.distribution.application;

import com.oriolvc.notificationsservice.distribution.application.model.ListDistributionListMembersCommand;
import com.oriolvc.notificationsservice.distribution.application.model.MemberView;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListNotFoundException;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ListDistributionListMembers {

    private final DistributionListRepository distributionListRepository;

    ListDistributionListMembers(final DistributionListRepository distributionListRepository) {
        this.distributionListRepository = distributionListRepository;
    }

    /** All members, removed ones included, in the order they were added. */
    public List<MemberView> execute(final ListDistributionListMembersCommand command) {
        return distributionListRepository
                .findByIdAndCustomerId(command.listId(), command.tenantId())
                .orElseThrow(() -> new DistributionListNotFoundException(command.listId()))
                .members()
                .stream()
                .map(MemberViews::of)
                .toList();
    }
}
