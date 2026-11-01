package com.oriolvc.notificationsservice.notification.domain;

import static com.oriolvc.notificationsservice.notification.domain.RegulatoryDocumentMatched.EMAIL_TEMPLATE;
import static com.oriolvc.notificationsservice.notification.domain.RegulatoryDocumentMatched.SCHEMA;
import static com.oriolvc.notificationsservice.notification.domain.RegulatoryDocumentMatched.json;
import static com.oriolvc.notificationsservice.notification.domain.RegulatoryDocumentMatched.sampleParameters;
import static org.assertj.core.api.Assertions.assertThat;

import com.oriolvc.notificationsservice.shared.domain.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

class TemplateRendererTest {

    @Test
    @DisplayName("AC-2 the sample parameters render exactly the expected message")
    void rendersTheSampleExactly() {
        final RenderedMessage message = TemplateRenderer.render(EMAIL_TEMPLATE, SCHEMA, sampleParameters());

        assertThat(message.body())
                .isEqualTo("Qualifyze Spain, located at Passeig de Gracia 19 Barcelona, received a FDA warning letter"
                        + " on 12 June 2026. [80%]");
        assertThat(message.subject()).isEqualTo("FDA warning letter – Qualifyze Spain");
    }

    @Test
    void enumValuesRenderTheirTitleAndDatesAreSpelledOutInEnglish() {
        final ObjectNode parameters =
                sampleParameters().put("documentType", "NON_COMPLIANCE_REPORT").put("issueDate", "2026-01-05");

        assertThat(TemplateRenderer.render(EMAIL_TEMPLATE, SCHEMA, parameters).body())
                .contains("received a FDA non-compliance report on 5 January 2026.");
    }

    @Test
    void lineBreaksAreRemovedFromSubjectValuesOnly() {
        final ObjectNode parameters = sampleParameters().put("siteName", "Evil\r\nBcc: someone@example.com");

        final RenderedMessage message = TemplateRenderer.render(EMAIL_TEMPLATE, SCHEMA, parameters);

        assertThat(message.subject()).isEqualTo("FDA warning letter – EvilBcc: someone@example.com");
        assertThat(message.body()).startsWith("Evil\r\nBcc: someone@example.com, located at");
    }

    @Test
    void valuesWithoutSchemaFormattingRenderAsTheyAreAndMissingOnesAsEmpty() {
        final ParametersSchema schema = new ParametersSchema(json("""
                {"type": "object", "properties": {"note": {"type": "string"}, "count": {"type": "integer"},
                 "flag": {"type": "boolean"}, "code": {"oneOf": [{"const": "A"}]}}}
                """));
        final Template template = new Template(Channel.EMAIL, null, "{{count}}|{{flag}}|{{code}}|{{note}}|{{ note }}");

        final RenderedMessage message =
                TemplateRenderer.render(template, schema, json("{\"count\": 3, \"flag\": true, \"code\": \"A\"}"));

        assertThat(message.subject()).isNull();
        assertThat(message.body()).isEqualTo("3|true|A||{{ note }}");
    }

    @Test
    void replacementCharactersInValuesAreTakenLiterally() {
        final ObjectNode parameters = sampleParameters().put("siteName", "Site $1 \\ backslash");

        assertThat(TemplateRenderer.render(EMAIL_TEMPLATE, SCHEMA, parameters).body())
                .startsWith("Site $1 \\ backslash, located at");
    }
}
