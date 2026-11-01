package com.oriolvc.notificationsservice.notification.application;

import com.oriolvc.notificationsservice.notification.domain.NotificationTypeRepository;
import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * RFC 8 startup validation: a template placeholder that is not in its type's schema would render as an empty string
 * in every message, so the application refuses to start instead.
 */
@Component
class TemplateCatalogCheck implements SmartInitializingSingleton {

    private final NotificationTypeRepository notificationTypeRepository;

    TemplateCatalogCheck(final NotificationTypeRepository notificationTypeRepository) {
        this.notificationTypeRepository = notificationTypeRepository;
    }

    @Override
    public void afterSingletonsInstantiated() {
        final List<String> problems = notificationTypeRepository.findAll().stream()
                .flatMap(type -> type.templateProblems().stream())
                .toList();
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid notification templates:\n  " + String.join("\n  ", problems));
        }
    }
}
