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

    static DistributionListView of(DistributionList list, String notificationTypeCode) {
        return new DistributionListView(
                list.id(),
                list.name(),
                notificationTypeCode,
                list.channel(),
                list.isActive(),
                list.version(),
                list.audit().createdAt(),
                list.audit().createdBy(),
                list.audit().updatedAt(),
                list.audit().updatedBy());
    }
}
