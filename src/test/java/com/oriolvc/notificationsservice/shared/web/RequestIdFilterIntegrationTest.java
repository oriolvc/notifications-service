package com.oriolvc.notificationsservice.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.oriolvc.notificationsservice.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class RequestIdFilterIntegrationTest extends IntegrationTest {

    @Test
    void echoesTheCallerRequestIdAndPutsItInProblemDetails() {
        MvcTestResult result = mvc.get()
                .uri("/notifications")
                .header(RequestIdFilter.HEADER, "abc-123")
                .exchange();

        assertThat(result).headers().hasValue(RequestIdFilter.HEADER, "abc-123");
        assertThat(result).bodyJson().extractingPath("$.requestId").isEqualTo("abc-123");
    }

    @Test
    void generatesARequestIdWhenMissingOrUnsafe() {
        MvcTestResult missing = mvc.get().uri("/actuator/health").exchange();
        MvcTestResult unsafe = mvc.get()
                .uri("/actuator/health")
                .header(RequestIdFilter.HEADER, "bad id\r\nX-Injected: 1")
                .exchange();

        assertThat(missing.getResponse().getHeader(RequestIdFilter.HEADER)).matches("[0-9a-f-]{36}");
        assertThat(unsafe.getResponse().getHeader(RequestIdFilter.HEADER)).matches("[0-9a-f-]{36}");
    }
}
