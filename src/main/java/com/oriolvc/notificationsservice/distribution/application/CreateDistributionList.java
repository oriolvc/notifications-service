package com.oriolvc.notificationsservice.distribution.application;

import com.oriolvc.notificationsservice.distribution.domain.DistributionList;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListRepository;
import com.oriolvc.notificationsservice.notification.application.NotificationTypeQuery;
import com.oriolvc.notificationsservice.notification.application.NotificationTypeRef;
import com.oriolvc.notificationsservice.shared.error.UnprocessableException;
import com.oriolvc.notificationsservice.shared.error.Violation;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateDistributionList {

    private final DistributionListRepository lists;
    private final NotificationTypeQuery notificationTypes;
    private final DistributionListViews views;

    CreateDistributionList(
            DistributionListRepository lists, NotificationTypeQuery notificationTypes, DistributionListViews views) {
        this.lists = lists;
        this.notificationTypes = notificationTypes;
        this.views = views;
    }

    @Transactional
    public DistributionListView execute(CreateDistributionListCommand command) {
        NotificationTypeRef type = notificationTypes
                .findActiveByCode(command.notificationType())
                .orElseThrow(() -> new UnprocessableException(
                        "Unknown or inactive notification type",
                        List.of(new Violation("/notificationType", "unknown or inactive notification type"))));
        // Duplicate names and a second active list are rejected by the unique constraints (409)
        DistributionList list = DistributionList.create(
                command.tenantId(), DistributionList.normalizeName(command.name()), type.id(), command.channel());
        return views.of(lists.save(list));
    }
}
