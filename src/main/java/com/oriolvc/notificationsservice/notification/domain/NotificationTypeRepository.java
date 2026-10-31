package com.oriolvc.notificationsservice.notification.domain;

import java.util.Optional;

public interface NotificationTypeRepository {

    Optional<NotificationType> findByCode(String code);

    Optional<NotificationType> findById(Long id);
}
