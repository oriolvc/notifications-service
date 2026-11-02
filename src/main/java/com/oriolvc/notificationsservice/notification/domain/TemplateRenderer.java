package com.oriolvc.notificationsservice.notification.domain;

import com.oriolvc.notificationsservice.notification.domain.parameters.ParametersSchema;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import tools.jackson.databind.JsonNode;

/**
 * Fills {@code {{name}}} placeholders with the parameters, formatted by the schema so that new types need no code
 * (RFC 8):
 *
 * <ul>
 *   <li>{@code format: date} → {@code d MMMM yyyy} in English ({@code 2026-06-12} → {@code 12 June 2026});
 *   <li>{@code oneOf} of {@code const}/{@code title} → the title ({@code WARNING_LETTER} → {@code warning letter});
 *   <li>anything else → its value; a missing optional parameter → empty.
 * </ul>
 *
 * The body is plain text. CR and LF are removed from values rendered into the subject (header injection).
 */
public final class TemplateRenderer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private TemplateRenderer() {}

    /** The parameters must be valid for the schema. */
    public static RenderedMessage render(
            final Template template, final ParametersSchema schema, final JsonNode parameters) {
        final String subject = template.subject() == null
                ? null
                : fill(template.subject(), schema, parameters, value -> value.replaceAll("[\\r\\n]", ""));
        return new RenderedMessage(subject, fill(template.body(), schema, parameters, UnaryOperator.identity()));
    }

    private static String fill(
            final String text,
            final ParametersSchema schema,
            final JsonNode parameters,
            final UnaryOperator<String> sanitize) {
        return Template.PLACEHOLDER.matcher(text).replaceAll(match -> {
            final String name = match.group(1);
            final String value =
                    format(parameters.get(name), schema.property(name).orElse(null));
            return Matcher.quoteReplacement(sanitize.apply(value));
        });
    }

    private static String format(final JsonNode value, final JsonNode propertySchema) {
        if (value == null || value.isNull()) {
            return "";
        }
        if (propertySchema != null) {
            if ("date".equals(propertySchema.path("format").stringValue(null))) {
                return LocalDate.parse(value.stringValue()).format(DATE);
            }
            for (final JsonNode option : propertySchema.path("oneOf").values()) {
                if (value.equals(option.get("const")) && option.has("title")) {
                    return option.get("title").stringValue();
                }
            }
        }
        return value.isString() ? value.stringValue() : value.toString();
    }
}
