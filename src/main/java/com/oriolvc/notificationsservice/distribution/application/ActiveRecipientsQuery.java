package com.oriolvc.notificationsservice.distribution.application;

import com.oriolvc.notificationsservice.distribution.application.model.Recipient;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Who receives a notification: the active members of the customer's active lists for the notification type, one list
 * per channel. Read by the notification context through its {@code RecipientResolver} port.
 */
@Service
public class ActiveRecipientsQuery {

    private final DistributionListRepository distributionListRepository;

    ActiveRecipientsQuery(final DistributionListRepository distributionListRepository) {
        this.distributionListRepository = distributionListRepository;
    }

    public List<Recipient> findActiveRecipients(final long customerId, final long notificationTypeId) {
        return distributionListRepository
                .findActiveByCustomerIdAndNotificationTypeId(customerId, notificationTypeId)
                .stream()
                .flatMap(distributionList -> distributionList.activeMembers().stream()
                        .map(member -> new Recipient(
                                distributionList.id(), member.id(), distributionList.channel(), member.address())))
                .toList();
    }
}
