package com.oriolvc.notificationsservice.notification.domain.parameters;

import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * RFC 7.3: strings are trimmed and every whitespace run becomes a single space, so cosmetic differences do not change
 * the event key (AC-7). Values are otherwise kept as they are, case included. The normalized parameters are what is
 * validated, stored and rendered.
 */
public final class ParameterNormalizer {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private ParameterNormalizer() {}

    public static JsonNode normalize(final JsonNode node) {
        if (node == null) {
            return JsonNodeFactory.instance.nullNode();
        }
        if (node.isString()) {
            return JsonNodeFactory.instance.stringNode(
                    WHITESPACE.matcher(node.stringValue()).replaceAll(" ").strip());
        }
        if (node.isObject()) {
            final ObjectNode normalized = JsonNodeFactory.instance.objectNode();
            node.properties().forEach(property -> normalized.set(property.getKey(), normalize(property.getValue())));
            return normalized;
        }
        if (node.isArray()) {
            final ArrayNode normalized = JsonNodeFactory.instance.arrayNode();
            node.values().forEach(value -> normalized.add(normalize(value)));
            return normalized;
        }
        return node;
    }
}
