package com.oriolvc.notificationsservice.shared.auditing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AuditingTest {

    private final AuditingConfig config = new AuditingConfig();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void auditorIsTheCallerOrSystem() {
        assertThat(config.auditorAware().getCurrentAuditor()).contains(Auditor.SYSTEM);

        authenticateAs("alice");

        assertThat(config.auditorAware().getCurrentAuditor()).contains("alice");
    }

    @Test
    void auditTimestampsComeFromTheClock() {
        Instant now = Instant.parse("2026-10-31T10:00:00Z");

        assertThat(config.auditingDateTimeProvider(Clock.fixed(now, ZoneOffset.UTC))
                        .getNow())
                .contains(now);
    }

    @Test
    void auditTimestampsAreTruncatedToWhatPostgresStores() {
        Instant now = Instant.parse("2026-10-31T10:00:00.123456789Z");

        assertThat(config.auditingDateTimeProvider(Clock.fixed(now, ZoneOffset.UTC))
                        .getNow())
                .contains(Instant.parse("2026-10-31T10:00:00.123456Z"));
    }

    @Test
    void enversRevisionsStoreTheSameUserId() throws Exception {
        authenticateAs("alice");
        var constructor = RevisionInfo.class.getDeclaredConstructor();
        RevisionInfo revision = constructor.newInstance();

        new UserRevisionListener().newRevision(revision);

        assertThat(revision.userId()).isEqualTo("alice");
        assertThat(revision.rev()).isNull();
        assertThat(revision.timestamp()).isNull();
    }

    private static void authenticateAs(String userId) {
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"),
                Map.of("userId", userId, "tenantId", 101, "roles", List.of("NOTIFICATION_VIEWER")));
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt, List.of(new SimpleGrantedAuthority("ROLE_NOTIFICATION_VIEWER")), userId));
    }
}
