package com.oriolvc.notificationsservice.notification.domain.ports;

import com.oriolvc.notificationsservice.notification.domain.NotificationType;
import java.util.List;
import java.util.Optional;

public interface NotificationTypeRepository {

    Optional<NotificationType> findByCode(String code);

    Optional<NotificationType> findById(long id);

    /** Active and inactive types, with their templates. */
    List<NotificationType> findAll();
}
