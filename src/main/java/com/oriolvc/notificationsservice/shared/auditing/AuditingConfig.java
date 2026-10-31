package com.oriolvc.notificationsservice.shared.auditing;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Fills {@code created_by/at} and {@code updated_by/at} from the caller and the injected {@link Clock}. */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditingDateTimeProvider")
class AuditingConfig {

    @Bean
    AuditorAware<String> auditorAware() {
        return () -> Optional.of(Auditor.current());
    }

    /** Truncated to what Postgres stores, so the saved copy and a later read carry the same timestamps. */
    @Bean
    DateTimeProvider auditingDateTimeProvider(final Clock clock) {
        return () -> Optional.of(clock.instant().truncatedTo(ChronoUnit.MICROS));
    }
}
