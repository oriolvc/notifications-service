package com.oriolvc.notificationsservice.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oriolvc.notificationsservice.IntegrationTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

/** Schema created by Liquibase (RFC 10): seed data and the constraints the application relies on. */
@Transactional
class DatabaseSchemaIntegrationTest extends IntegrationTest {

    private static final String TYPE_ID =
            "(SELECT id FROM notification_types WHERE code = 'REGULATORY_DOCUMENT_MATCHED')";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsAllTables() {
        assertThat(jdbc.queryForList(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class))
                .contains(
                        "notification_types",
                        "notification_templates",
                        "distribution_lists",
                        "distribution_list_members",
                        "notifications",
                        "notification_deliveries",
                        "delivery_attempts",
                        "revinfo",
                        "distribution_lists_aud",
                        "distribution_list_members_aud")
                .doesNotContain("seed_parameters_schemas");
    }

    @Nested
    class Seed {

        @Test
        void regulatoryDocumentMatchedTypeStoresTheSchemaFileTheSpecReferences() throws IOException {
            String schemaFile = new ClassPathResource("schemas/regulatory-document-matched.v1.json")
                    .getContentAsString(StandardCharsets.UTF_8);

            assertThat(jdbc.queryForObject(
                            "SELECT active AND parameters_schema = ?::jsonb FROM notification_types"
                                    + " WHERE code = 'REGULATORY_DOCUMENT_MATCHED'",
                            Boolean.class,
                            schemaFile))
                    .isTrue();
        }

        @Test
        void regulatoryDocumentMatchedHasTheEmailTemplate() {
            var template = jdbc.queryForMap(
                    "SELECT subject, body FROM notification_templates WHERE channel = 'EMAIL' AND notification_type_id = "
                            + TYPE_ID);

            assertThat(template)
                    .containsEntry("subject", "{{authorityName}} {{documentType}} – {{siteName}}")
                    .containsEntry(
                            "body",
                            "{{siteName}}, located at {{siteAddress}}, received a {{authorityName}} {{documentType}}"
                                    + " on {{issueDate}}. [{{confidence}}%]");
        }
    }

    @Nested
    @Sql("/fixtures/schema-baseline.sql")
    class Constraints {

        @Test
        void sameEventIsStoredOncePerCustomer() {
            String insert =
                    "INSERT INTO notifications (notification_type_id, event_key, customer_id, parameters, outcome,"
                            + " created_at, created_by) VALUES (" + TYPE_ID
                            + ", repeat('a', 64), ?, '{}', 'ACCEPTED', now(), 'p')";

            assertThat(jdbc.update(insert + " ON CONFLICT DO NOTHING", 101)).isZero();
            assertThat(jdbc.update(insert, 102)).isOne();
            assertThatThrownBy(() -> jdbc.update(insert, 101))
                    .isInstanceOf(DuplicateKeyException.class)
                    .hasMessageContaining("uq_notifications_type_event_customer");
        }

        @Test
        void eventKeyMustBeLowercaseSha256Hex() {
            assertThatThrownBy(() -> jdbc.update(
                            "INSERT INTO notifications (notification_type_id, event_key, customer_id, parameters,"
                                    + " outcome, created_at, created_by) VALUES (" + TYPE_ID
                                    + ", repeat('A', 64), 101, '{}', 'ACCEPTED', now(), 'p')"))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_notifications_event_key");
        }

        @Test
        void customerHasAtMostOneActiveListPerTypeAndChannel() {
            assertThat(insertList("Old list", false)).isOne();
            assertThatThrownBy(() -> insertList("Second list", true))
                    .isInstanceOf(DuplicateKeyException.class)
                    .hasMessageContaining("uq_distribution_lists_active_customer_type_channel");
        }

        @Test
        void listNamesAreUniquePerCustomer() {
            assertThatThrownBy(() -> insertList("Compliance team", false))
                    .isInstanceOf(DuplicateKeyException.class)
                    .hasMessageContaining("uq_distribution_lists_customer_name");
        }

        @Test
        void memberAddressIsUniquePerListIgnoringCase() {
            assertThatThrownBy(() -> jdbc.update(
                            "INSERT INTO distribution_list_members (distribution_list_id, address, created_at,"
                                    + " created_by, updated_at, updated_by)"
                                    + " VALUES (1001, 'quality@customer101.example', now(), 'u', now(), 'u')"))
                    .isInstanceOf(DuplicateKeyException.class)
                    .hasMessageContaining("uq_distribution_list_members_list_address");
        }

        @Test
        void emailTemplateRequiresSubject() {
            jdbc.update("DELETE FROM notification_deliveries");
            jdbc.update("DELETE FROM notification_templates");

            assertThatThrownBy(() -> jdbc.update(
                            "INSERT INTO notification_templates (notification_type_id, channel, body, created_at,"
                                    + " created_by, updated_at, updated_by)"
                                    + " VALUES (" + TYPE_ID + ", 'EMAIL', 'body', now(), 's', now(), 's')"))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_notification_templates_email_subject");
        }

        @Test
        void unknownChannelIsRejected() {
            assertThatThrownBy(() -> jdbc.update("UPDATE distribution_lists SET channel = 'SLACK' WHERE id = 1001"))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_distribution_lists_channel");
        }

        @Test
        void deliveryStatusIsRestricted() {
            assertThatThrownBy(
                            () -> jdbc.update("UPDATE notification_deliveries SET status = 'SENDING' WHERE id = 4001"))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_notification_deliveries_status");
        }

        @Test
        void attemptNumberIsUniquePerDelivery() {
            String insert = "INSERT INTO delivery_attempts (delivery_id, attempt_number, attempted_at, outcome)"
                    + " VALUES (4001, 1, now(), 'RETRYABLE_ERROR')";

            assertThat(jdbc.update(insert)).isOne();
            assertThatThrownBy(() -> jdbc.update(insert))
                    .isInstanceOf(DuplicateKeyException.class)
                    .hasMessageContaining("uq_delivery_attempts_delivery_attempt_number");
        }

        private int insertList(String name, boolean active) {
            return jdbc.update(
                    "INSERT INTO distribution_lists (customer_id, name, notification_type_id, channel, active,"
                            + " created_at, created_by, updated_at, updated_by)"
                            + " VALUES (101, ?, " + TYPE_ID + ", 'EMAIL', ?, now(), 'u', now(), 'u')",
                    name,
                    active);
        }
    }
}
