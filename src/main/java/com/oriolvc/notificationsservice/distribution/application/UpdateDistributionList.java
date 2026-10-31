package com.oriolvc.notificationsservice.distribution.application;

import com.oriolvc.notificationsservice.distribution.domain.ActiveDistributionListExistsException;
import com.oriolvc.notificationsservice.distribution.domain.DistributionList;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListNameTakenException;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListNotFoundException;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateDistributionList {

    private final DistributionListRepository lists;
    private final DistributionListViews views;
    private final UniqueListGuard guard;

    UpdateDistributionList(DistributionListRepository lists, DistributionListViews views, UniqueListGuard guard) {
        this.lists = lists;
        this.views = views;
        this.guard = guard;
    }

    @Transactional
    public DistributionListView execute(UpdateDistributionListCommand command) {
        DistributionList list = lists.findByIdAndCustomerId(command.listId(), command.tenantId())
                .orElseThrow(() -> new DistributionListNotFoundException(command.listId()));
        list.checkVersion(command.version());
        DistributionListView current = views.of(list);

        String name = DistributionList.normalizeName(command.name());
        if (name != null && !name.equals(list.name())) {
            if (lists.existsByCustomerIdAndName(command.tenantId(), name)) {
                throw new DistributionListNameTakenException(name);
            }
            list.rename(name);
        }
        if (Boolean.TRUE.equals(command.active()) && !list.isActive()) {
            if (lists.existsByCustomerIdAndNotificationTypeIdAndChannelAndActiveTrue(
                    list.customerId(), list.notificationTypeId(), list.channel())) {
                throw new ActiveDistributionListExistsException(current.notificationType(), list.channel());
            }
            list.activate();
        } else if (Boolean.FALSE.equals(command.active())) {
            list.deactivate();
        }
        return views.of(guard.saveAndFlush(list, current.notificationType()));
    }
}
