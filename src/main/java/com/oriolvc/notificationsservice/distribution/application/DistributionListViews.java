package com.oriolvc.notificationsservice.distribution.application;

import com.oriolvc.notificationsservice.distribution.domain.DistributionList;
import com.oriolvc.notificationsservice.notification.application.NotificationTypeQuery;
import com.oriolvc.notificationsservice.notification.application.NotificationTypeRef;
import org.springframework.stereotype.Component;

/** Builds views, resolving the notification type code through the notification context. */
@Component
class DistributionListViews {

    private final NotificationTypeQuery notificationTypes;

    DistributionListViews(NotificationTypeQuery notificationTypes) {
        this.notificationTypes = notificationTypes;
    }

    DistributionListView of(DistributionList list) {
        String code = notificationTypes
                .findById(list.notificationTypeId())
                .map(NotificationTypeRef::code)
                .orElseThrow(() -> new IllegalStateException(
                        "Distribution list " + list.id() + " references a missing notification type"));
        return DistributionListView.of(list, code);
    }
}
