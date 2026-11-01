package com.oriolvc.notificationsservice.distribution.infrastructure.web;

import com.oriolvc.notificationsservice.distribution.application.model.DistributionListView;
import com.oriolvc.notificationsservice.generated.model.Channel;
import com.oriolvc.notificationsservice.generated.model.DistributionList;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Generated DTOs ↔ application types. Generated code never leaves this package. */
final class DistributionListWebMapper {

    private DistributionListWebMapper() {}

    static DistributionList toDto(DistributionListView view) {
        return new DistributionList()
                .id(view.id())
                .name(view.name())
                .notificationType(view.notificationType())
                .channel(Channel.fromValue(view.channel().name()))
                .active(view.active())
                .version(view.version())
                .createdAt(toOffset(view.createdAt()))
                .createdBy(view.createdBy())
                .updatedAt(toOffset(view.updatedAt()))
                .updatedBy(view.updatedBy());
    }

    static com.oriolvc.notificationsservice.shared.domain.Channel toDomain(Channel channel) {
        return com.oriolvc.notificationsservice.shared.domain.Channel.valueOf(channel.getValue());
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
