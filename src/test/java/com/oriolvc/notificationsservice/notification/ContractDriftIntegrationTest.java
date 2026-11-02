package com.oriolvc.notificationsservice.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.oriolvc.notificationsservice.IntegrationTest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Drift guard (RFC 6.3): the active notification types in the database are exactly the request variants of the spec,
 * and each stored parameters schema is the file its variant references.
 */
class ContractDriftIntegrationTest extends IntegrationTest {

    private static final String SPEC = "openapi/notifications-service.yaml";

    @Test
    void activeTypesMatchTheSpecVariantsAndTheirSchemaFiles() throws IOException {
        final Map<String, JsonNode> stored = new HashMap<>();
        jdbc.query("SELECT code, parameters_schema::text AS schema FROM notification_types WHERE active", row -> {
            stored.put(row.getString("code"), JsonMapper.shared().readTree(row.getString("schema")));
        });
        final Map<String, JsonNode> specified = specifiedSchemas();

        assertThat(stored).isNotEmpty();
        assertThat(specified).isEqualTo(stored);
    }

    /** Discriminator value → the parameters schema file its variant references, following local {@code $ref}s. */
    @SuppressWarnings("unchecked")
    private static Map<String, JsonNode> specifiedSchemas() throws IOException {
        final Map<String, Object> spec;
        try (InputStream in = new ClassPathResource(SPEC).getInputStream()) {
            spec = new Yaml().load(in);
        }
        final Map<String, Object> schemas =
                (Map<String, Object>) ((Map<String, Object>) spec.get("components")).get("schemas");
        final Map<String, Object> request = (Map<String, Object>) schemas.get("CreateNotificationRequest");
        final Map<String, String> mapping =
                (Map<String, String>) ((Map<String, Object>) request.get("discriminator")).get("mapping");
        final Map<String, JsonNode> result = new HashMap<>();
        for (final Map.Entry<String, String> variant : mapping.entrySet()) {
            final Map<String, Object> variantSchema = (Map<String, Object>) resolve(schemas, variant.getValue());
            final Map<String, Object> properties = (Map<String, Object>) variantSchema.get("properties");
            assertThat(((Map<String, Object>) properties.get("type")).get("const"))
                    .as("const type of the %s variant", variant.getKey())
                    .isEqualTo(variant.getKey());
            String ref = (String) ((Map<String, Object>) properties.get("parameters")).get("$ref");
            while (ref.startsWith("#/")) {
                ref = (String) ((Map<String, Object>) resolve(schemas, ref)).get("$ref");
            }
            result.put(
                    variant.getKey(),
                    JsonMapper.shared().readTree(classpath(URI.create(SPEC).resolve(ref))));
        }
        return result;
    }

    private static Object resolve(final Map<String, Object> schemas, final String localRef) {
        return schemas.get(localRef.substring("#/components/schemas/".length()));
    }

    private static String classpath(final URI path) throws IOException {
        return new ClassPathResource(path.getPath()).getContentAsString(StandardCharsets.UTF_8);
    }
}
