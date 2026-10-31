package com.oriolvc.notificationsservice.notification.infrastructure.persistence;

import com.oriolvc.notificationsservice.notification.domain.NotificationType;
import com.oriolvc.notificationsservice.notification.domain.NotificationTypeRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
class JpaNotificationTypeRepository implements NotificationTypeRepository {

    private final NotificationTypeDao dao;

    JpaNotificationTypeRepository(NotificationTypeDao dao) {
        this.dao = dao;
    }

    @Override
    public Optional<NotificationType> findByCode(String code) {
        return dao.findByCode(code).map(NotificationTypeDboMapper::toDomain);
    }

    @Override
    public Optional<NotificationType> findById(long id) {
        return dao.findById(id).map(NotificationTypeDboMapper::toDomain);
    }
}
