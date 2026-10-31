package com.oriolvc.notificationsservice.distribution.application;

import com.oriolvc.notificationsservice.distribution.application.model.DistributionListView;
import com.oriolvc.notificationsservice.distribution.domain.DistributionList;
import com.oriolvc.notificationsservice.notification.application.NotificationTypeQuery;
import com.oriolvc.notificationsservice.notification.application.model.NotificationTypeRef;
import org.springframework.stereotype.Component;

/** Builds views, resolving the notification type code through the notification context. */
@Component
class DistributionListViews {

    private final NotificationTypeQuery notificationTypes;

    DistributionListViews(final NotificationTypeQuery notificationTypes) {
        this.notificationTypes = notificationTypes;
    }

    DistributionListView of(final DistributionList distributionList) {
        final String code = notificationTypes
                .findById(distributionList.notificationTypeId())
                .map(NotificationTypeRef::code)
                .orElseThrow(() -> new IllegalStateException(
                        "Distribution list " + distributionList.id() + " references a missing notification type"));
        return new DistributionListView(
                distributionList.id(),
                distributionList.name(),
                code,
                distributionList.channel(),
                distributionList.isActive(),
                distributionList.version(),
                distributionList.audit().createdAt(),
                distributionList.audit().createdBy(),
                distributionList.audit().updatedAt(),
                distributionList.audit().updatedBy());
    }
}
