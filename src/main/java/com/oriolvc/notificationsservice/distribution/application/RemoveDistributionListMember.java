package com.oriolvc.notificationsservice.distribution.application;

import com.oriolvc.notificationsservice.distribution.application.model.RemoveDistributionListMemberCommand;
import com.oriolvc.notificationsservice.distribution.application.ports.DistributionMetrics;
import com.oriolvc.notificationsservice.distribution.application.ports.DistributionMetrics.ConfigChange;
import com.oriolvc.notificationsservice.distribution.domain.DistributionList;
import com.oriolvc.notificationsservice.distribution.domain.exceptions.DistributionListNotFoundException;
import com.oriolvc.notificationsservice.distribution.domain.ports.DistributionListRepository;
import org.springframework.stereotype.Service;

/** Soft delete (D-5). Idempotent: removing an inactive member changes nothing. */
@Service
public class RemoveDistributionListMember {

    private final DistributionListRepository distributionListRepository;
    private final DistributionMetrics metrics;

    RemoveDistributionListMember(
            final DistributionListRepository distributionListRepository, final DistributionMetrics metrics) {
        this.distributionListRepository = distributionListRepository;
        this.metrics = metrics;
    }

    public void execute(final RemoveDistributionListMemberCommand command) {
        final DistributionList distributionList = distributionListRepository
                .findByIdAndCustomerId(command.listId(), command.tenantId())
                .orElseThrow(() -> new DistributionListNotFoundException(command.listId()));
        if (distributionList.member(command.memberId()).isActive()) {
            distributionList.removeMember(command.memberId());
            distributionListRepository.save(distributionList);
            metrics.configChanged(ConfigChange.MEMBER_REMOVED);
        }
    }
}
