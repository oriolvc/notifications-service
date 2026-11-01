package com.oriolvc.notificationsservice.notification.domain;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.erdtman.jcs.JsonCanonicalizer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** RFC 8785 (JCS): sorted keys, no insignificant whitespace, canonical numbers and string escapes. */
final class CanonicalJson {

    private CanonicalJson() {}

    static String of(final JsonNode node) {
        try {
            return new JsonCanonicalizer(JsonMapper.shared().writeValueAsString(node)).getEncodedString();
        } catch (final IOException e) {
            throw new UncheckedIOException("Cannot canonicalize JSON", e);
        }
    }
}
