package com.oriolvc.notificationsservice.notification.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Queries on {@code notification_types}. Only {@link JpaNotificationTypeRepository} uses it. */
interface NotificationTypeDao extends Repository<NotificationTypeDbo, Long> {

    Optional<NotificationTypeDbo> findByCode(String code);

    Optional<NotificationTypeDbo> findById(long id);

    List<NotificationTypeDbo> findAllByOrderByIdAsc();
}
