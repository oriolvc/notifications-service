package com.oriolvc.notificationsservice.notification.domain;

import java.util.Objects;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/** The JSON Schema (draft 2020-12) of a type's parameters (RFC 7.2). Also drives how values are rendered (RFC 8). */
public record ParametersSchema(JsonNode json) {

    public ParametersSchema {
        Objects.requireNonNull(json, "json");
        if (!json.isObject()) {
            throw new IllegalArgumentException("A parameters schema must be a JSON object");
        }
    }

    public boolean hasProperty(final String name) {
        return property(name).isPresent();
    }

    /** The subschema of a top-level property. */
    public Optional<JsonNode> property(final String name) {
        return json.path("properties").optional(name);
    }
}
