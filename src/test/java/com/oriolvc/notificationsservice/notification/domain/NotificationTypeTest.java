package com.oriolvc.notificationsservice.notification.domain;

import static com.oriolvc.notificationsservice.notification.domain.RegulatoryDocumentMatched.SCHEMA;
import static com.oriolvc.notificationsservice.notification.domain.RegulatoryDocumentMatched.TYPE;
import static com.oriolvc.notificationsservice.notification.domain.RegulatoryDocumentMatched.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oriolvc.notificationsservice.shared.domain.Channel;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationTypeTest {

    @Test
    void theSeededTemplateOnlyUsesSchemaProperties() {
        assertThat(TYPE.templateProblems()).isEmpty();
        assertThat(TYPE.template(Channel.EMAIL)).isPresent();
        assertThat(RegulatoryDocumentMatched.EMAIL_TEMPLATE.placeholders())
                .containsExactly("authorityName", "confidence", "documentType", "issueDate", "siteAddress", "siteName");
    }

    @Test
    void placeholdersMissingFromTheSchemaAreReported() {
        final Template template = new Template(Channel.EMAIL, "{{siteNam}}", "{{siteName}} {{issuedOn}}");
        final NotificationType type =
                new NotificationType(1, "T", "Type", true, SCHEMA, Map.of(Channel.EMAIL, template));

        assertThat(type.templateProblems())
                .containsExactly(
                        "The EMAIL template of T uses {{issuedOn}}, which is not a property of its parameters schema",
                        "The EMAIL template of T uses {{siteNam}}, which is not a property of its parameters schema");
    }

    @Test
    void aSchemaIsAJsonObject() {
        assertThatThrownBy(() -> new ParametersSchema(json("[]"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(SCHEMA.property("issueDate")).isPresent();
        assertThat(SCHEMA.hasProperty("unknown")).isFalse();
    }
}
