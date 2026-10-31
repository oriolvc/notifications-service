package com.oriolvc.notificationsservice.notification.infrastructure.persistence;

import com.oriolvc.notificationsservice.notification.domain.NotificationType;

final class NotificationTypeDboMapper {

    private NotificationTypeDboMapper() {}

    static NotificationType toDomain(final NotificationTypeDbo dbo) {
        return new NotificationType(dbo.getId(), dbo.getCode(), dbo.getName(), dbo.isActive());
    }
}
