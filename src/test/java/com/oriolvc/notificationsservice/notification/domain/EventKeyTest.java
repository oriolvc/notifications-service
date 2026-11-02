package com.oriolvc.notificationsservice.notification.domain;

import static com.oriolvc.notificationsservice.notification.domain.RegulatoryDocumentMatched.CODE;
import static com.oriolvc.notificationsservice.notification.domain.RegulatoryDocumentMatched.json;
import static com.oriolvc.notificationsservice.notification.domain.RegulatoryDocumentMatched.sampleParameters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oriolvc.notificationsservice.notification.domain.parameters.CanonicalJson;
import com.oriolvc.notificationsservice.notification.domain.parameters.ParameterNormalizer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class EventKeyTest {

    @Test
    @DisplayName("AC-7 extra whitespace in a value gives the same event key")
    void whitespaceDoesNotChangeTheKey() {
        final JsonNode noisy = sampleParameters()
                .put("siteName", "  Qualifyze \t Spain\n")
                .put("siteAddress", "Passeig de Gracia   19 Barcelona");

        assertThat(key(noisy)).isEqualTo(key(sampleParameters()));
    }

    @Test
    @DisplayName("AC-8 a different confidence gives a new event key")
    void confidenceChangesTheKey() {
        assertThat(key(sampleParameters().put("confidence", 81))).isNotEqualTo(key(sampleParameters()));
    }

    @Test
    void caseAndTypeMatter() {
        assertThat(key(sampleParameters().put("siteName", "QUALIFYZE SPAIN"))).isNotEqualTo(key(sampleParameters()));
        assertThat(EventKey.of("OTHER_TYPE", ParameterNormalizer.normalize(sampleParameters())))
                .isNotEqualTo(key(sampleParameters()));
    }

    @Test
    void propertyOrderDoesNotMatter() {
        final JsonNode reordered = json("""
                {"confidence": 80, "issueDate": "2026-06-12", "documentType": "WARNING_LETTER",
                 "authorityName": "FDA", "siteAddress": "Passeig de Gracia 19 Barcelona", "siteName": "Qualifyze Spain"}
                """);

        assertThat(key(reordered)).isEqualTo(key(sampleParameters()));
    }

    @Test
    void isTheSha256OfTheTypeCodeAndTheCanonicalJson() throws Exception {
        final String canonical = "{\"authorityName\":\"FDA\",\"confidence\":80,\"documentType\":\"WARNING_LETTER\","
                + "\"issueDate\":\"2026-06-12\",\"siteAddress\":\"Passeig de Gracia 19 Barcelona\","
                + "\"siteName\":\"Qualifyze Spain\"}";
        final String expected = HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256")
                        .digest((CODE + "\n" + canonical).getBytes(StandardCharsets.UTF_8)));

        assertThat(CanonicalJson.of(sampleParameters())).isEqualTo(canonical);
        assertThat(key(sampleParameters()).value()).isEqualTo(expected);
        assertThat(key(sampleParameters())).hasToString(expected);
    }

    @Test
    void canonicalJsonFollowsRfc8785() {
        assertThat(CanonicalJson.of(json("{\"b\": 1.0, \"a\": [1e2, -0.0, 0.5], \"é\": \"€\\n\"}")))
                .isEqualTo("{\"a\":[100,0,0.5],\"b\":1,\"é\":\"€\\n\"}");
    }

    @Test
    void onlyLowercaseSha256HexIsAKey() {
        assertThatThrownBy(() -> new EventKey("A".repeat(64))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventKey("a".repeat(63))).isInstanceOf(IllegalArgumentException.class);
    }

    private static EventKey key(final JsonNode parameters) {
        return EventKey.of(CODE, ParameterNormalizer.normalize(parameters));
    }
}
