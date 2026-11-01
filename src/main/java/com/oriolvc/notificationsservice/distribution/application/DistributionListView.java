package com.oriolvc.notificationsservice.distribution.application;

import com.oriolvc.notificationsservice.distribution.domain.DistributionList;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import java.time.Instant;

public record DistributionListView(
        long id,
        String name,
        String notificationType,
        Channel channel,
        boolean active,
        long version,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {

    static DistributionListView of(DistributionList distributionList, String notificationTypeCode) {
        return new DistributionListView(
                distributionList.id(),
                distributionList.name(),
                notificationTypeCode,
                distributionList.channel(),
                distributionList.isActive(),
                distributionList.version(),
                distributionList.audit().createdAt(),
                distributionList.audit().createdBy(),
                distributionList.audit().updatedAt(),
                distributionList.audit().updatedBy());
    }
}
