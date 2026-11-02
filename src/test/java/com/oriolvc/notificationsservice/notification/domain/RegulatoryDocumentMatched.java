package com.oriolvc.notificationsservice.notification.domain;

import com.oriolvc.notificationsservice.notification.domain.parameters.ParametersSchema;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The seeded type for unit tests: the real v1 schema file and the email template of RFC 8 (the seed itself is checked
 * by {@code DatabaseSchemaIntegrationTest}).
 */
public final class RegulatoryDocumentMatched {

    public static final String CODE = "REGULATORY_DOCUMENT_MATCHED";
    public static final ParametersSchema SCHEMA =
            new ParametersSchema(json(classpath("schemas/regulatory-document-matched.v1.json")));
    public static final Template EMAIL_TEMPLATE = new Template(
            1,
            Channel.EMAIL,
            "{{authorityName}} {{documentType}} – {{siteName}}",
            "{{siteName}}, located at {{siteAddress}}, received a {{authorityName}} {{documentType}} on {{issueDate}}."
                    + " [{{confidence}}%]");
    public static final NotificationType TYPE = new NotificationType(
            1, CODE, "Regulatory document matched", true, SCHEMA, Map.of(Channel.EMAIL, EMAIL_TEMPLATE));

    private RegulatoryDocumentMatched() {}

    /** The sample event of RFC 8 / AC-2; a fresh copy each time, so tests can change it. */
    public static ObjectNode sampleParameters() {
        return (ObjectNode) json("""
                {"siteName": "Qualifyze Spain", "siteAddress": "Passeig de Gracia 19 Barcelona",
                 "authorityName": "FDA", "documentType": "WARNING_LETTER", "issueDate": "2026-06-12",
                 "confidence": 80}
                """);
    }

    public static JsonNode json(final String text) {
        return JsonMapper.shared().readTree(text);
    }

    private static String classpath(final String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
