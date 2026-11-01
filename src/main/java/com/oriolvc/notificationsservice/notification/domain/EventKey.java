package com.oriolvc.notificationsservice.notification.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * Identifies an event for deduplication and is its reference in every response and record (RFC 7.3, FR.1.6):
 * {@code hex(SHA-256(typeCode + "\n" + canonicalJson(normalizedParameters)))}.
 */
public record EventKey(String value) {

    private static final Pattern SHA_256_HEX = Pattern.compile("^[0-9a-f]{64}$");

    public EventKey {
        Objects.requireNonNull(value, "value");
        if (!SHA_256_HEX.matcher(value).matches()) {
            throw new IllegalArgumentException("An event key is a lowercase hex SHA-256");
        }
    }

    /** The parameters must already be normalized ({@link ParameterNormalizer}). */
    public static EventKey of(final String typeCode, final JsonNode normalizedParameters) {
        final String input = typeCode + "\n" + CanonicalJson.of(normalizedParameters);
        return new EventKey(HexFormat.of().formatHex(sha256().digest(input.getBytes(StandardCharsets.UTF_8))));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JVM provides SHA-256", e);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
