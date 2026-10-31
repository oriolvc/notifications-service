package com.oriolvc.notificationsservice.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.oriolvc.notificationsservice.IntegrationTest;
import com.oriolvc.notificationsservice.JwtTokens;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class JwtAuthenticationIntegrationTest extends IntegrationTest {

    private static final String VALID_BODY = """
            {"type": "REGULATORY_DOCUMENT_MATCHED", "customerIds": [101],
             "parameters": {"siteName": "Qualifyze Spain", "siteAddress": "Passeig de Gracia 19 Barcelona",
                            "authorityName": "FDA", "documentType": "WARNING_LETTER",
                            "issueDate": "2026-06-12", "confidence": 80}}
            """;

    @DisplayName("AC-17 customer user calling POST /notifications gets 403")
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"DISTRIBUTION_LIST_ADMIN", "NOTIFICATION_VIEWER"})
    void customerUserCannotPublish(final String role) {
        final MvcTestResult result = postNotification(JwtTokens.user(101, Role.valueOf(role)));

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("urn:notifications-service:problem:forbidden");
        assertThat(result).headers().containsHeader(HttpHeaders.WWW_AUTHENTICATE);
    }

    @Test
    void publisherPassesAuthorization() {
        assertThat(postNotification(JwtTokens.publisher())).hasStatus(HttpStatus.NOT_IMPLEMENTED);
    }

    @Test
    void missingTokenIsReportedAsProblemDetail() {
        final MvcTestResult result = mvc.get().uri("/distribution-lists").exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED).hasContentType("application/problem+json");
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("urn:notifications-service:problem:unauthorized");
    }

    @Test
    void expiredTokenIsRejected() {
        final String expired = JwtTokens.token(claims -> withAdminClaims(claims)
                .issuedAt(Instant.now().minus(Duration.ofHours(2)))
                .expiresAt(Instant.now().minus(Duration.ofHours(1))));

        assertUnauthorized(expired);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        assertUnauthorized(JwtTokens.token(claims -> withAdminClaims(claims).issuer("https://evil.example")));
    }

    @Test
    void tokenForAnotherAudienceIsRejected() {
        assertUnauthorized(JwtTokens.token(claims -> withAdminClaims(claims).audience(List.of("other-service"))));
    }

    @Test
    void tokenSignedWithUnknownKeyIsRejected() {
        assertUnauthorized(JwtTokens.signedWithForeignKey(JwtAuthenticationIntegrationTest::withAdminClaims));
    }

    @Test
    void tenantScopedRoleWithoutTenantIsRejected() {
        assertUnauthorized(JwtTokens.token(claims -> withAdminClaims(claims).claim("tenantId", "")));
        assertUnauthorized(JwtTokens.token(claims ->
                claims.subject("u").claim("userId", "u").claim("roles", List.of(Role.DISTRIBUTION_LIST_ADMIN.name()))));
    }

    @Test
    void tokenWithoutKnownRolesIsForbidden() {
        final String token = JwtTokens.token(
                claims -> claims.subject("u").claim("userId", "u").claim("roles", List.of("SUPER_ADMIN")));

        assertThat(mvc.get().uri("/distribution-lists").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    private void assertUnauthorized(final String token) {
        final MvcTestResult result = mvc.get()
                .uri("/distribution-lists")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).contains("invalid_token");
    }

    private MvcTestResult postNotification(final String token) {
        return mvc.post()
                .uri("/notifications")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .exchange();
    }

    private static JwtClaimsSet.Builder withAdminClaims(final JwtClaimsSet.Builder claims) {
        return claims.subject("u")
                .claim("userId", "u")
                .claim("tenantId", 101)
                .claim("roles", List.of(Role.DISTRIBUTION_LIST_ADMIN.name()));
    }
}
