package com.oriolvc.notificationsservice.notification.domain.parameters;

import static com.oriolvc.notificationsservice.notification.domain.RegulatoryDocumentMatched.json;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ParameterNormalizerTest {

    @Test
    void trimsAndCollapsesWhitespaceInEveryStringAndKeepsTheRest() {
        assertThat(ParameterNormalizer.normalize(json("""
                        {" key ": "  a \\t\\n b ", "n": 1.5, "ok": true, "none": null,
                         "list": [" x  y ", {"inner": " z "}]}
                        """))).isEqualTo(json("""
                        {" key ": "a b", "n": 1.5, "ok": true, "none": null, "list": ["x y", {"inner": "z"}]}
                        """));
    }

    @Test
    void missingParametersBecomeNull() {
        assertThat(ParameterNormalizer.normalize(null).isNull()).isTrue();
    }
}
