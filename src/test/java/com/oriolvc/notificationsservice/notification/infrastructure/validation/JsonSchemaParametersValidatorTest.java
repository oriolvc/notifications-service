package com.oriolvc.notificationsservice.notification.infrastructure.validation;

import static com.oriolvc.notificationsservice.notification.domain.RegulatoryDocumentMatched.SCHEMA;
import static com.oriolvc.notificationsservice.notification.domain.RegulatoryDocumentMatched.json;
import static com.oriolvc.notificationsservice.notification.domain.RegulatoryDocumentMatched.sampleParameters;
import static org.assertj.core.api.Assertions.assertThat;

import com.oriolvc.notificationsservice.notification.domain.ParametersSchema;
import com.oriolvc.notificationsservice.shared.error.Violation;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

class JsonSchemaParametersValidatorTest {

    private final JsonSchemaParametersValidator validator = new JsonSchemaParametersValidator();

    @Test
    void theSampleIsValid() {
        assertThat(validator.validate(SCHEMA, sampleParameters())).isEmpty();
    }

    @Test
    @DisplayName("AC-12 every violation is reported with a pointer to the parameter")
    void reportsEveryViolation() {
        final ObjectNode parameters = sampleParameters()
                .put("confidence", 101)
                .put("documentType", "PRESS_RELEASE")
                .put("issueDate", "2026-02-30")
                .put("unexpected", "x");
        parameters.remove("siteName");

        assertThat(pointers(validator.validate(SCHEMA, parameters)))
                .containsExactlyInAnyOrder("/confidence", "/documentType", "/issueDate", "/unexpected", "/siteName");
    }

    @Test
    void violationsCarryAReadableMessage() {
        final List<Violation> violations =
                validator.validate(SCHEMA, sampleParameters().put("confidence", 101));

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.pointer()).isEqualTo("/confidence");
            assertThat(violation.message()).contains("100");
        });
    }

    @Test
    void anInvalidEnumerationValueListsTheAllowedValues() {
        assertThat(validator.validate(SCHEMA, sampleParameters().put("documentType", "PRESS_RELEASE")))
                .containsExactly(new Violation(
                        "/documentType", "must be one of \"WARNING_LETTER\", \"OAI\", \"NON_COMPLIANCE_REPORT\""));
    }

    @Test
    void otherAlternativesKeepTheLibraryMessage() {
        final ParametersSchema schema = new ParametersSchema(json("""
                {"type": "object", "properties": {"id": {"oneOf": [{"type": "string"}, {"type": "integer"}]}}}
                """));

        assertThat(validator.validate(schema, json("{\"id\": true}")))
                .singleElement()
                .satisfies(violation -> assertThat(violation.pointer()).isEqualTo("/id"));
    }

    @Test
    void parametersMustBeAnObjectOfTheRightTypes() {
        assertThat(pointers(validator.validate(SCHEMA, json("[]")))).containsExactly("");
        assertThat(pointers(validator.validate(SCHEMA, sampleParameters().put("confidence", "80"))))
                .containsExactly("/confidence");
        assertThat(validator.validate(SCHEMA, null)).isNotEmpty();
    }

    private static List<String> pointers(final List<Violation> violations) {
        return violations.stream().map(Violation::pointer).toList();
    }
}
