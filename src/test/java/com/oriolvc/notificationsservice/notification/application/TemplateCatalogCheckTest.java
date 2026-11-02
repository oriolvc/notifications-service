package com.oriolvc.notificationsservice.notification.application;

import static com.oriolvc.notificationsservice.notification.domain.RegulatoryDocumentMatched.SCHEMA;
import static com.oriolvc.notificationsservice.notification.domain.RegulatoryDocumentMatched.TYPE;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oriolvc.notificationsservice.notification.domain.NotificationType;
import com.oriolvc.notificationsservice.notification.domain.Template;
import com.oriolvc.notificationsservice.notification.domain.ports.NotificationTypeRepository;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TemplateCatalogCheckTest {

    @Test
    void consistentTemplatesLetTheApplicationStart() {
        assertThatCode(() -> check(TYPE).afterSingletonsInstantiated()).doesNotThrowAnyException();
    }

    @Test
    void aPlaceholderMissingFromTheSchemaStopsTheApplication() {
        final NotificationType broken = new NotificationType(
                2,
                "BROKEN",
                "Broken",
                false,
                SCHEMA,
                Map.of(Channel.EMAIL, new Template(9, Channel.EMAIL, "s", "{{x}}")));

        assertThatThrownBy(() -> check(TYPE, broken).afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("The EMAIL template of BROKEN uses {{x}}");
    }

    private static TemplateCatalogCheck check(final NotificationType... types) {
        return new TemplateCatalogCheck(new NotificationTypeRepository() {
            @Override
            public Optional<NotificationType> findByCode(final String code) {
                return Optional.empty();
            }

            @Override
            public Optional<NotificationType> findById(final long id) {
                return Optional.empty();
            }

            @Override
            public List<NotificationType> findAll() {
                return List.of(types);
            }
        });
    }
}
