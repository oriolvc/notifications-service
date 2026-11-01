package com.oriolvc.notificationsservice.distribution.application.model;

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
        String updatedBy) {}
