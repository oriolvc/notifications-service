package com.oriolvc.notificationsservice.distribution.application;

import com.oriolvc.notificationsservice.distribution.domain.ActiveDistributionListExistsException;
import com.oriolvc.notificationsservice.distribution.domain.DistributionList;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListNameTakenException;
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
    private final UniqueListGuard guard;

    CreateDistributionList(
            DistributionListRepository lists,
            NotificationTypeQuery notificationTypes,
            DistributionListViews views,
            UniqueListGuard guard) {
        this.lists = lists;
        this.notificationTypes = notificationTypes;
        this.views = views;
        this.guard = guard;
    }

    @Transactional
    public DistributionListView execute(CreateDistributionListCommand command) {
        NotificationTypeRef type = notificationTypes
                .findActiveByCode(command.notificationType())
                .orElseThrow(() -> new UnprocessableException(
                        "Unknown or inactive notification type",
                        List.of(new Violation("/notificationType", "unknown or inactive notification type"))));
        String name = DistributionList.normalizeName(command.name());
        if (lists.existsByCustomerIdAndName(command.tenantId(), name)) {
            throw new DistributionListNameTakenException(name);
        }
        if (lists.existsByCustomerIdAndNotificationTypeIdAndChannelAndActiveTrue(
                command.tenantId(), type.id(), command.channel())) {
            throw new ActiveDistributionListExistsException(type.code(), command.channel());
        }
        DistributionList list = DistributionList.create(command.tenantId(), name, type.id(), command.channel());
        return views.of(guard.saveAndFlush(list, type.code()));
    }
}
