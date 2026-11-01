package com.oriolvc.notificationsservice.distribution.application;

import com.oriolvc.notificationsservice.distribution.application.model.RemoveDistributionListMemberCommand;
import com.oriolvc.notificationsservice.distribution.domain.DistributionList;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListNotFoundException;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListRepository;
import org.springframework.stereotype.Service;

/** Soft delete (D-5). Idempotent: removing an inactive member changes nothing. */
@Service
public class RemoveDistributionListMember {

    private final DistributionListRepository distributionListRepository;

    RemoveDistributionListMember(final DistributionListRepository distributionListRepository) {
        this.distributionListRepository = distributionListRepository;
    }

    public void execute(final RemoveDistributionListMemberCommand command) {
        final DistributionList distributionList = distributionListRepository
                .findByIdAndCustomerId(command.listId(), command.tenantId())
                .orElseThrow(() -> new DistributionListNotFoundException(command.listId()));
        if (distributionList.member(command.memberId()).isActive()) {
            distributionList.removeMember(command.memberId());
            distributionListRepository.save(distributionList);
        }
    }
}
