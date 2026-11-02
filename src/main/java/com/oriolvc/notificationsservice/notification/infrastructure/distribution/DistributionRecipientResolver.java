package com.oriolvc.notificationsservice.notification.infrastructure.distribution;

import com.oriolvc.notificationsservice.distribution.application.ActiveRecipientsQuery;
import com.oriolvc.notificationsservice.notification.application.RecipientResolver;
import com.oriolvc.notificationsservice.notification.domain.Recipient;
import java.util.List;
import org.springframework.stereotype.Component;

/** {@link RecipientResolver} over the distribution context's application layer; never its domain or repositories. */
@Component
class DistributionRecipientResolver implements RecipientResolver {

    private final ActiveRecipientsQuery activeRecipientsQuery;

    DistributionRecipientResolver(final ActiveRecipientsQuery activeRecipientsQuery) {
        this.activeRecipientsQuery = activeRecipientsQuery;
    }

    @Override
    public List<Recipient> resolve(final long customerId, final long notificationTypeId) {
        return activeRecipientsQuery.findActiveRecipients(customerId, notificationTypeId).stream()
                .map(recipient -> new Recipient(
                        recipient.distributionListId(), recipient.memberId(), recipient.channel(), recipient.address()))
                .toList();
    }
}
