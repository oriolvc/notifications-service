package com.oriolvc.notificationsservice.distribution.infrastructure.persistence;

import com.oriolvc.notificationsservice.distribution.domain.DistributionList;
import com.oriolvc.notificationsservice.distribution.domain.Member;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

final class DistributionListDboMapper {

    private DistributionListDboMapper() {}

    static DistributionList toDomain(final DistributionListDbo dbo) {
        return DistributionList.restore(
                dbo.getId(),
                dbo.getCustomerId(),
                dbo.getName(),
                dbo.getNotificationTypeId(),
                dbo.getChannel(),
                dbo.isActive(),
                dbo.getVersion(),
                dbo.auditMetadata(),
                dbo.getMembers().stream()
                        .map(DistributionListDboMapper::toDomain)
                        .toList());
    }

    static Member toDomain(final MemberDbo dbo) {
        return Member.restore(dbo.getId(), dbo.getAddress(), dbo.isActive(), dbo.auditMetadata());
    }

    /** Copies the domain state onto the row; id, version and audit columns are managed by JPA. */
    static void copy(final DistributionList distributionList, final DistributionListDbo dbo) {
        dbo.setCustomerId(distributionList.customerId());
        dbo.setName(distributionList.name());
        dbo.setNotificationTypeId(distributionList.notificationTypeId());
        dbo.setChannel(distributionList.channel());
        dbo.setActive(distributionList.isActive());
    }

    /**
     * Brings the member rows in line with the aggregate: new members become new rows, existing ones get their
     * {@code active} flag. Members are never deleted.
     *
     * @return whether any member changed
     */
    static boolean copyMembers(final DistributionList distributionList, final DistributionListDbo dbo) {
        final Map<Long, MemberDbo> rows =
                dbo.getMembers().stream().collect(Collectors.toMap(MemberDbo::getId, Function.identity()));
        boolean changed = false;
        for (final Member member : distributionList.members()) {
            if (member.id() == null) {
                dbo.getMembers().add(new MemberDbo(dbo, member.address(), member.isActive()));
                changed = true;
            } else {
                final MemberDbo row = rows.get(member.id());
                if (row.isActive() != member.isActive()) {
                    row.setActive(member.isActive());
                    changed = true;
                }
            }
        }
        return changed;
    }
}
