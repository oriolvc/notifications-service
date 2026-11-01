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
    static void copy(DistributionList distributionList, DistributionListDbo dbo) {
        dbo.setCustomerId(distributionList.customerId());
        dbo.setName(distributionList.name());
        dbo.setNotificationTypeId(distributionList.notificationTypeId());
        dbo.setChannel(distributionList.channel());
        dbo.setActive(distributionList.isActive());
    }
}
