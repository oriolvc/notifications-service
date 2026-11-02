package com.oriolvc.notificationsservice.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.oriolvc.notificationsservice.IntegrationTest;
import com.oriolvc.notificationsservice.notification.domain.NotificationType;
import com.oriolvc.notificationsservice.notification.domain.RegulatoryDocumentMatched;
import com.oriolvc.notificationsservice.notification.domain.ports.NotificationTypeRepository;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The seeded type, read back with its schema and templates. */
class JpaNotificationTypeRepositoryIntegrationTest extends IntegrationTest {

    @Autowired
    private NotificationTypeRepository notificationTypeRepository;

    @Test
    void readsTheSeededTypeWithItsSchemaAndEmailTemplate() {
        final NotificationType type = notificationTypeRepository
                .findByCode(RegulatoryDocumentMatched.CODE)
                .orElseThrow();

        assertThat(type.active()).isTrue();
        assertThat(type.parametersSchema()).isEqualTo(RegulatoryDocumentMatched.SCHEMA);
        assertThat(type.template(Channel.EMAIL)).contains(RegulatoryDocumentMatched.EMAIL_TEMPLATE);
        assertThat(notificationTypeRepository.findAll()).containsExactly(type);
        assertThat(notificationTypeRepository.findById(type.id())).contains(type);
    }
}
