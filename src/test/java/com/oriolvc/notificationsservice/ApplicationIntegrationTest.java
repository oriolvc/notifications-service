package com.oriolvc.notificationsservice;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ApplicationIntegrationTest extends IntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoadsAgainstPostgres18WithLiquibaseApplied() {
        final JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(jdbc.queryForObject("SHOW server_version_num", Integer.class))
                .isGreaterThanOrEqualTo(180000);
        assertThat(jdbc.queryForObject("SELECT to_regclass('databasechangelog') IS NOT NULL", Boolean.class))
                .isTrue();
    }
}
