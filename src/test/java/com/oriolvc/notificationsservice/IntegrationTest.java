package com.oriolvc.notificationsservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Base class for integration tests:
 *
 * <ul>
 *   <li>Postgres 18 container, a Spring bean shared by every test class reusing the same cached context;
 *   <li>{@link #mvc} validating every exchange against the OpenAPI spec ({@link OpenApiContractMatcher}), and
 *       {@link #get}/{@link #post}/{@link #patch}/{@link #delete} helpers sending a bearer token;
 *   <li>a clean database before each test ({@link CleanDatabaseTestExecutionListener});
 *   <li>tokens from {@link JwtTokens}.
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, OpenApiValidationConfiguration.class})
@TestExecutionListeners(
        listeners = CleanDatabaseTestExecutionListener.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public abstract class IntegrationTest {

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    protected static String bearer(final String token) {
        return JwtTokens.bearer(token);
    }

    /** The response is a Problem Details of the given type slug ({@code urn:notifications-service:problem:<slug>}). */
    protected static void assertProblem(final MvcTestResult result, final HttpStatus status, final String type) {
        assertThat(result).hasStatus(status);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("urn:notifications-service:problem:" + type);
    }

    protected MvcTestResult get(final String uri, final String token) {
        return mvc.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange();
    }

    protected MvcTestResult post(final String uri, final String token, final String body) {
        return mvc.post()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    protected MvcTestResult patch(final String uri, final String token, final String body) {
        return mvc.patch()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    protected MvcTestResult delete(final String uri, final String token) {
        return mvc.delete()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange();
    }
}
