package com.oriolvc.notificationsservice.distribution.application;

import com.oriolvc.notificationsservice.distribution.application.model.AddDistributionListMemberCommand;
import com.oriolvc.notificationsservice.distribution.application.model.MemberView;
import com.oriolvc.notificationsservice.distribution.domain.DistributionList;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListNotFoundException;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListRepository;
import com.oriolvc.notificationsservice.distribution.domain.Member;
import org.springframework.stereotype.Service;

/** Adds an address to a list of the caller's tenant, or reactivates it if it was removed. */
@Service
public class AddDistributionListMember {

    private final DistributionListRepository distributionListRepository;
    private final AddressValidators addressValidators;

    AddDistributionListMember(
            final DistributionListRepository distributionListRepository, final AddressValidators addressValidators) {
        this.distributionListRepository = distributionListRepository;
        this.addressValidators = addressValidators;
    }

    public MemberView execute(final AddDistributionListMemberCommand command) {
        final DistributionList distributionList = distributionListRepository
                .findByIdAndCustomerId(command.listId(), command.tenantId())
                .orElseThrow(() -> new DistributionListNotFoundException(command.listId()));
        final Member added =
                distributionList.addMember(command.address(), addressValidators.forChannel(distributionList.channel()));
        final DistributionList saved = distributionListRepository.save(distributionList);
        return MemberViews.of(saved.memberWithAddress(added.address()).orElseThrow());
    }
}
