package com.oriolvc.notificationsservice.distribution.infrastructure.web;

import com.oriolvc.notificationsservice.distribution.application.model.DistributionListView;
import com.oriolvc.notificationsservice.distribution.application.model.MemberView;
import com.oriolvc.notificationsservice.generated.model.Channel;
import com.oriolvc.notificationsservice.generated.model.DistributionList;
import com.oriolvc.notificationsservice.generated.model.Member;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Generated DTOs ↔ application types. Generated code never leaves this package. */
final class DistributionListWebMapper {

    private DistributionListWebMapper() {}

    static DistributionList toDto(final DistributionListView view) {
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

    static Member toDto(final MemberView view) {
        return new Member()
                .id(view.id())
                .address(view.address())
                .active(view.active())
                .createdAt(toOffset(view.createdAt()))
                .createdBy(view.createdBy())
                .updatedAt(toOffset(view.updatedAt()))
                .updatedBy(view.updatedBy());
    }

    static com.oriolvc.notificationsservice.shared.domain.Channel toDomain(final Channel channel) {
        return com.oriolvc.notificationsservice.shared.domain.Channel.valueOf(channel.getValue());
    }

    private static OffsetDateTime toOffset(final Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
