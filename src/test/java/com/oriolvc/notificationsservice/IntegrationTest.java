package com.oriolvc.notificationsservice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Base class for integration tests:
 *
 * <ul>
 *   <li>Postgres 18 container, a Spring bean shared by every test class reusing the same cached context;
 *   <li>{@link #mvc} validating every exchange against the OpenAPI spec ({@link OpenApiContractMatcher});
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

    protected static String bearer(String token) {
        return JwtTokens.bearer(token);
    }
}
