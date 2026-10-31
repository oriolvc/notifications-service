package com.oriolvc.notificationsservice.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.oriolvc.notificationsservice.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiDocumentationIntegrationTest extends IntegrationTest {

    @Test
    void servesTheSpecAndTheSchemasItReferencesWithoutAuthentication() {
        assertThat(mvc.get().uri("/openapi/notifications-service.yaml"))
                .hasStatusOk()
                .bodyText()
                .contains("openapi: 3.1.0", "'../schemas/regulatory-document-matched.v1.json'");

        assertThat(mvc.get().uri("/schemas/regulatory-document-matched.v1.json"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.properties.documentType.oneOf[0].const")
                .isEqualTo("WARNING_LETTER");
    }

    @Test
    void swaggerUiIsPublicAndPointsAtTheStaticSpec() {
        assertThat(mvc.get().uri("/swagger-ui/index.html")).hasStatusOk();

        assertThat(mvc.get().uri("/v3/api-docs/swagger-config"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.url")
                .isEqualTo(ApiDocumentationConfig.SPEC_PATH);
    }

    @Test
    void springdocGeneratedSpecIsNotExposed() {
        assertThat(mvc.get().uri("/v3/api-docs")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/v3/api-docs.yaml")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void doesNotExposeOtherClasspathResources() {
        assertThat(mvc.get().uri("/application.yaml")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/openapi/../application.yaml")).hasStatus4xxClientError();
    }
}
