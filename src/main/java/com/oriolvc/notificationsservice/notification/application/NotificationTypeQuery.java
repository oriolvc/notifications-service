package com.oriolvc.notificationsservice.notification.application;

import com.oriolvc.notificationsservice.notification.application.model.NotificationTypeRef;
import com.oriolvc.notificationsservice.notification.domain.NotificationType;
import com.oriolvc.notificationsservice.notification.domain.NotificationTypeRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** The notification type catalog, as seen from other contexts (e.g. distribution lists validating their type). */
@Service
public class NotificationTypeQuery {

    private final NotificationTypeRepository types;

    public NotificationTypeQuery(final NotificationTypeRepository types) {
        this.types = types;
    }

    public Optional<NotificationTypeRef> findActiveByCode(final String code) {
        return types.findByCode(code).filter(NotificationType::active).map(NotificationTypeQuery::toRef);
    }

    /** Any type, active or not: existing lists keep referencing a type that was deactivated. */
    public Optional<NotificationTypeRef> findById(final long id) {
        return types.findById(id).map(NotificationTypeQuery::toRef);
    }

    /** Every type, active or not. */
    public List<NotificationTypeRef> findAll() {
        return types.findAll().stream().map(NotificationTypeQuery::toRef).toList();
    }

    private static NotificationTypeRef toRef(final NotificationType type) {
        return new NotificationTypeRef(type.id(), type.code());
    }
}
