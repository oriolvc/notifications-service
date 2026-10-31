package com.oriolvc.notificationsservice.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.oriolvc.notificationsservice.IntegrationTest;
import com.oriolvc.notificationsservice.JwtTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Malformed requests reach the generated interfaces and come back as Problem Details matching the spec. */
class ProblemDetailsIntegrationTest extends IntegrationTest {

    private static final String MALFORMED_REQUEST = "urn:notifications-service:problem:malformed-request";

    @Test
    void unreadableBodyIsMalformed() {
        final MvcTestResult result = postNotification("{\"type\": ");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType("application/problem+json");
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(MALFORMED_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo("/notifications");
    }

    @Test
    void beanValidationErrorsListViolationsAsJsonPointers() {
        final MvcTestResult result = postNotification("""
                {"type": "REGULATORY_DOCUMENT_MATCHED", "customerIds": [], "parameters": {}}
                """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(MALFORMED_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.violations[0].pointer").isEqualTo("/customerIds");
    }

    @Test
    void invalidPathParameterIsMalformed() {
        final MvcTestResult result = mvc.get()
                .uri("/distribution-lists/0")
                .header(HttpHeaders.AUTHORIZATION, bearer(JwtTokens.viewer(101)))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.violations[0].pointer").isEqualTo("listId");
    }

    @Test
    void nonNumericPathParameterIsMalformed() {
        final MvcTestResult result = mvc.get()
                .uri("/distribution-lists/abc")
                .header(HttpHeaders.AUTHORIZATION, bearer(JwtTokens.viewer(101)))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(MALFORMED_REQUEST);
    }

    @Test
    void unsupportedContentTypeIsReported() {
        final MvcTestResult result = mvc.post()
                .uri("/notifications")
                .header(HttpHeaders.AUTHORIZATION, bearer(JwtTokens.publisher()))
                .contentType(MediaType.TEXT_PLAIN)
                .content("hello")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("urn:notifications-service:problem:unsupported-media-type");
    }

    private MvcTestResult postNotification(final String body) {
        return mvc.post()
                .uri("/notifications")
                .header(HttpHeaders.AUTHORIZATION, bearer(JwtTokens.publisher()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}
