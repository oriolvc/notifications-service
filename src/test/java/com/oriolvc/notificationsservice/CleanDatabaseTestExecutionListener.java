package com.oriolvc.notificationsservice;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;

/**
 * Truncates everything except the Liquibase seed before each test. Ordered after
 * {@link TransactionalTestExecutionListener} (so it joins a test transaction) and before the {@code @Sql} listener (so
 * fixtures survive).
 */
class CleanDatabaseTestExecutionListener extends AbstractTestExecutionListener {

    /** Created and seeded by Liquibase; never truncated. */
    private static final List<String> SEEDED_TABLES =
            List.of("databasechangelog", "databasechangeloglock", "notification_types", "notification_templates");

    @Override
    public int getOrder() {
        return 4500;
    }

    @Override
    public void beforeTestMethod(TestContext testContext) {
        JdbcTemplate jdbc = testContext.getApplicationContext().getBean(JdbcTemplate.class);
        List<String> tables = jdbc
                .queryForList(
                        "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename", String.class)
                .stream()
                .filter(table -> !SEEDED_TABLES.contains(table))
                .toList();
        jdbc.execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
    }
}
