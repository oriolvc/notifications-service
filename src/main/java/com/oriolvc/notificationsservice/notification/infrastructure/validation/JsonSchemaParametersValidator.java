package com.oriolvc.notificationsservice.notification.infrastructure.validation;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.PathType;
import com.oriolvc.notificationsservice.notification.domain.ParametersSchema;
import com.oriolvc.notificationsservice.notification.domain.ParametersValidator;
import com.oriolvc.notificationsservice.shared.error.Violation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * networknt json-schema-validator, draft 2020-12, with {@code format} asserted (an invalid {@code date} is a violation,
 * not just an annotation). Schemas and parameters cross over as JSON text: the library is on Jackson 2, the
 * application on Jackson 3.
 */
@Component
class JsonSchemaParametersValidator implements ParametersValidator {

    /** Keywords that report on the object; their {@code property} names the offending member. */
    private static final Set<String> MEMBER_KEYWORDS = Set.of("required", "additionalProperties", "dependentRequired");

    private final SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
            SpecificationVersion.DRAFT_2020_12,
            builder -> builder.schemaRegistryConfig(SchemaRegistryConfig.builder()
                    .formatAssertionsEnabled(true)
                    .pathType(PathType.JSON_POINTER)
                    .build()));

    /** Compiled schemas by their JSON text; types are few and never change at runtime. */
    private final Map<String, Schema> compiled = new ConcurrentHashMap<>();

    @Override
    public List<Violation> validate(final ParametersSchema schema, final JsonNode parameters) {
        final Schema compiledSchema =
                compiled.computeIfAbsent(schema.json().toString(), json -> registry.getSchema(json, InputFormat.JSON));
        return compiledSchema.validate(String.valueOf(parameters), InputFormat.JSON).stream()
                .filter(error -> !insideAlternative(error))
                .map(error -> toViolation(error, schema))
                .distinct()
                .toList();
    }

    /** A failed branch of {@code oneOf}/{@code anyOf}; the combinator's own error already reports the value. */
    private static boolean insideAlternative(final Error error) {
        final String path = error.getEvaluationPath().toString();
        return path.contains("/oneOf/") || path.contains("/anyOf/");
    }

    private static Violation toViolation(final Error error, final ParametersSchema schema) {
        final String location = error.getInstanceLocation().toString();
        final String pointer = MEMBER_KEYWORDS.contains(error.getKeyword()) && error.getProperty() != null
                ? location + "/" + error.getProperty()
                : location;
        return new Violation(pointer, message(error, schema));
    }

    /** An enumeration written as {@code oneOf} of {@code const}s reads better as the list of allowed values. */
    private static String message(final Error error, final ParametersSchema schema) {
        if ("oneOf".equals(error.getKeyword())) {
            final JsonNode options = schema.json().at(error.getEvaluationPath().toString());
            if (options.isArray()
                    && !options.isEmpty()
                    && options.values().stream().allMatch(o -> o.has("const"))) {
                return "must be one of "
                        + options.values().stream()
                                .map(option -> option.get("const").toString())
                                .collect(Collectors.joining(", "));
            }
        }
        return error.getMessage();
    }
}
