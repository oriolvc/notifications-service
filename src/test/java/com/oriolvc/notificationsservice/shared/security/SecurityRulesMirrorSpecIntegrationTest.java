package com.oriolvc.notificationsservice.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.oriolvc.notificationsservice.IntegrationTest;
import com.oriolvc.notificationsservice.JwtTokens;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.yaml.snakeyaml.Yaml;

/** The SecurityFilterChain must enforce exactly the {@code x-required-roles} of every operation in the spec. */
class SecurityRulesMirrorSpecIntegrationTest extends IntegrationTest {

    private static final long TENANT = 101;

    record Operation(String id, HttpMethod method, String path, Set<Role> roles) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    @ParameterizedTest(name = "{0} without a token → 401")
    @MethodSource("operations")
    void anonymousCallerIsUnauthenticated(Operation operation) {
        MvcTestResult result = mvc.perform(request(operation, null));

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).headers().containsHeader(HttpHeaders.WWW_AUTHENTICATE);
    }

    @ParameterizedTest(name = "{0} as {1} → {2}")
    @MethodSource("operationsByRole")
    void onlyTheRolesInTheSpecAreAllowed(Operation operation, Role role, String expectation) {
        MvcTestResult result = mvc.perform(request(operation, token(role)));

        if (operation.roles().contains(role)) {
            assertThat(result.getResponse().getStatus())
                    .as("%s must be allowed for %s", operation, role)
                    .isNotIn(401, 403);
        } else {
            assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        }
    }

    static Stream<Operation> operations() throws IOException {
        return readOperations().stream();
    }

    static Stream<Arguments> operationsByRole() throws IOException {
        return readOperations().stream()
                .flatMap(operation -> Stream.of(Role.values())
                        .map(role ->
                                Arguments.of(operation, role, operation.roles().contains(role) ? "allowed" : "403")));
    }

    private RequestBuilder request(Operation operation, String token) {
        var builder = MockMvcRequestBuilders.request(
                operation.method(), operation.path().replaceAll("\\{[^}]+}", "1"));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        return builder;
    }

    private static String token(Role role) {
        return role.isTenantScoped() ? JwtTokens.user(TENANT, role) : JwtTokens.publisher();
    }

    @SuppressWarnings("unchecked")
    private static List<Operation> readOperations() throws IOException {
        Map<String, Object> spec;
        try (InputStream in = new ClassPathResource("openapi/notifications-service.yaml").getInputStream()) {
            spec = new Yaml().load(in);
        }
        List<Operation> operations = new ArrayList<>();
        var paths = (Map<String, Map<String, Object>>) spec.get("paths");
        paths.forEach((path, item) -> item.forEach((method, value) -> {
            if (value instanceof Map<?, ?> operation && operation.containsKey("operationId")) {
                Set<Role> roles = EnumSet.noneOf(Role.class);
                ((List<String>) operation.get("x-required-roles")).forEach(role -> roles.add(Role.valueOf(role)));
                operations.add(new Operation(
                        (String) operation.get("operationId"), HttpMethod.valueOf(method.toUpperCase()), path, roles));
            }
        }));
        assertThat(operations).hasSize(11);
        return operations;
    }
}
