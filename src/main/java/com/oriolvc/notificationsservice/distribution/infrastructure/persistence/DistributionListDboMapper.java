package com.oriolvc.notificationsservice.distribution.infrastructure.persistence;

import com.oriolvc.notificationsservice.distribution.domain.DistributionList;

final class DistributionListDboMapper {

    private DistributionListDboMapper() {}

    static DistributionList toDomain(DistributionListDbo dbo) {
        return DistributionList.restore(
                dbo.getId(),
                dbo.getCustomerId(),
                dbo.getName(),
                dbo.getNotificationTypeId(),
                dbo.getChannel(),
                dbo.isActive(),
                dbo.getVersion(),
                dbo.auditMetadata());
    }

    /** Copies the domain state onto the row; id, version and audit columns are managed by JPA. */
    static void copy(DistributionList list, DistributionListDbo dbo) {
        dbo.setCustomerId(list.customerId());
        dbo.setName(list.name());
        dbo.setNotificationTypeId(list.notificationTypeId());
        dbo.setChannel(list.channel());
        dbo.setActive(list.isActive());
    }
}
