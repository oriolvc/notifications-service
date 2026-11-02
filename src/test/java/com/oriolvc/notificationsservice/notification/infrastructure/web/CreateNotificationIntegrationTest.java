package com.oriolvc.notificationsservice.notification.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.jayway.jsonpath.JsonPath;
import com.oriolvc.notificationsservice.IntegrationTest;
import com.oriolvc.notificationsservice.JwtTokens;
import com.oriolvc.notificationsservice.notification.application.model.NotificationsAccepted;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@Sql({"/fixtures/distribution-lists.sql", "/fixtures/recipients-edge-cases.sql"})
@RecordApplicationEvents
class CreateNotificationIntegrationTest extends IntegrationTest {

    private static final String PUBLISHER = JwtTokens.publisher();
    private static final String SITE_NAME = "Qualifyze Spain";
    private static final String BODY =
            "Qualifyze Spain, located at Passeig de Gracia 19 Barcelona, received a FDA warning letter on 12 June 2026."
                    + " [80%]";

    @Autowired
    private ApplicationEvents events;

    @Nested
    class Accepted {

        @Test
        @DisplayName("AC-1 one PENDING delivery per active member, with the rendered content and an address snapshot")
        void createsPendingDeliveries() {
            final MvcTestResult result = publish(List.of(101L, 102L), event(SITE_NAME, 80));

            assertThat(result).hasStatus(HttpStatus.ACCEPTED);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.eventKey")
                    .asString()
                    .matches("^[0-9a-f]{64}$");
            assertThat(result).bodyJson().isLenientlyEqualTo("""
                    {"results": [
                      {"customerId": 101, "outcome": "ACCEPTED", "deliveries": 2},
                      {"customerId": 102, "outcome": "ACCEPTED", "deliveries": 1}]}
                    """);
            assertThat(jdbc.queryForList("""
                            SELECT d.distribution_list_id, d.distribution_list_member_id, d.address, d.channel,
                                   d.subject, d.body, d.status, d.attempt_count, n.customer_id, n.outcome,
                                   n.created_by, d.template_id = t.id AS seeded_template
                            FROM notification_deliveries d
                            JOIN notifications n ON n.id = d.notification_id
                            JOIN notification_templates t ON t.channel = d.channel
                            ORDER BY d.distribution_list_member_id
                            """))
                    .extracting(
                            row -> row.get("distribution_list_member_id"),
                            row -> row.get("address"),
                            row -> row.get("subject"),
                            row -> row.get("body"),
                            row -> row.get("status"),
                            row -> row.get("attempt_count"),
                            row -> row.get("outcome"),
                            row -> row.get("created_by"),
                            row -> row.get("seeded_template"))
                    .containsExactly(
                            delivery(1101, "alice@customer101.example"),
                            delivery(1102, "bob@customer101.example"),
                            delivery(2101, "dave@customer102.example"));
        }

        @Test
        void storesTheNormalizedParametersAndTheEventKey() {
            final String eventKey = eventKeyOf(publish(List.of(101L), event("  Qualifyze \\t Spain ", 80)));

            assertThat(jdbc.queryForMap("SELECT event_key, parameters->>'siteName' AS site_name FROM notifications"))
                    .containsEntry("event_key", eventKey)
                    .containsEntry("site_name", SITE_NAME);
        }

        @Test
        void publishesTheAcceptedNotificationsForTheDispatcher() {
            publish(List.of(101L, 102L, 103L), event(SITE_NAME, 80));

            final List<Long> withDeliveries = jdbc.queryForList(
                    "SELECT id FROM notifications WHERE outcome = 'ACCEPTED' ORDER BY id", Long.class);
            assertThat(events.stream(NotificationsAccepted.class))
                    .singleElement()
                    .extracting(NotificationsAccepted::notificationIds)
                    .isEqualTo(withDeliveries);
        }
    }

    @Nested
    class NoRecipients {

        @Test
        @DisplayName("AC-10 no list, an inactive list or no active members: NO_RECIPIENTS is stored, others unaffected")
        void customersWithoutRecipients() {
            final MvcTestResult result = publish(List.of(103L, 104L, 105L, 102L), event(SITE_NAME, 80));

            assertThat(result).hasStatus(HttpStatus.ACCEPTED);
            assertThat(result).bodyJson().isLenientlyEqualTo("""
                    {"results": [
                      {"customerId": 103, "outcome": "NO_RECIPIENTS", "deliveries": 0},
                      {"customerId": 104, "outcome": "NO_RECIPIENTS", "deliveries": 0},
                      {"customerId": 105, "outcome": "NO_RECIPIENTS", "deliveries": 0},
                      {"customerId": 102, "outcome": "ACCEPTED", "deliveries": 1}]}
                    """);
            assertThat(jdbc.queryForList("SELECT customer_id, outcome FROM notifications ORDER BY customer_id"))
                    .extracting(row -> row.get("customer_id"), row -> row.get("outcome"))
                    .containsExactly(
                            tuple(102L, "ACCEPTED"),
                            tuple(103L, "NO_RECIPIENTS"),
                            tuple(104L, "NO_RECIPIENTS"),
                            tuple(105L, "NO_RECIPIENTS"));
            assertThat(count("notification_deliveries")).isEqualTo(1);
        }

        @Test
        void onlyNoRecipientsIsStillA202AndPublishesNothing() {
            assertThat(publish(List.of(103L), event(SITE_NAME, 80))).hasStatus(HttpStatus.ACCEPTED);
            assertThat(events.stream(NotificationsAccepted.class)).isEmpty();
        }
    }

    @Nested
    class Deduplication {

        @Test
        @DisplayName("AC-5 the same event posted twice is a 200 with every customer DUPLICATE and no new deliveries")
        void sameEventTwice() {
            final MvcTestResult first = publish(List.of(101L, 103L), event(SITE_NAME, 80));
            final MvcTestResult second = publish(List.of(101L, 103L), event(SITE_NAME, 80));

            assertThat(second).hasStatus(HttpStatus.OK);
            assertThat(eventKeyOf(second)).isEqualTo(eventKeyOf(first));
            assertThat(second).bodyJson().isLenientlyEqualTo("""
                    {"results": [
                      {"customerId": 101, "outcome": "DUPLICATE", "deliveries": 2},
                      {"customerId": 103, "outcome": "DUPLICATE", "deliveries": 0}]}
                    """);
            assertThat(notificationIds(second)).isEqualTo(notificationIds(first));
            assertThat(count("notifications")).isEqualTo(2);
            assertThat(count("notification_deliveries")).isEqualTo(2);
            assertThat(events.stream(NotificationsAccepted.class)).hasSize(1);
        }

        @Test
        @DisplayName("AC-6 the same event with an extra customer notifies only the new customer")
        void extraCustomer() {
            publish(List.of(101L), event(SITE_NAME, 80));

            final MvcTestResult result = publish(List.of(101L, 102L), event(SITE_NAME, 80));

            assertThat(result).hasStatus(HttpStatus.ACCEPTED);
            assertThat(result).bodyJson().isLenientlyEqualTo("""
                    {"results": [
                      {"customerId": 101, "outcome": "DUPLICATE", "deliveries": 2},
                      {"customerId": 102, "outcome": "ACCEPTED", "deliveries": 1}]}
                    """);
            assertThat(count("notification_deliveries")).isEqualTo(3);
        }

        @Test
        @DisplayName("AC-7 extra whitespace in siteName is the same event")
        void whitespaceIsTheSameEvent() {
            final MvcTestResult first = publish(List.of(101L), event(SITE_NAME, 80));
            final MvcTestResult second = publish(List.of(101L), event(" Qualifyze   Spain ", 80));

            assertThat(second).hasStatus(HttpStatus.OK);
            assertThat(eventKeyOf(second)).isEqualTo(eventKeyOf(first));
        }

        @Test
        @DisplayName("AC-8 a different confidence is a new event")
        void confidenceChangeIsANewEvent() {
            final MvcTestResult first = publish(List.of(101L), event(SITE_NAME, 80));
            final MvcTestResult second = publish(List.of(101L), event(SITE_NAME, 90));

            assertThat(second).hasStatus(HttpStatus.ACCEPTED);
            assertThat(eventKeyOf(second)).isNotEqualTo(eventKeyOf(first));
            assertThat(count("notifications")).isEqualTo(2);
        }

        @Test
        @DisplayName("AC-9 two identical requests in parallel store one notification per customer")
        void parallelRequests() throws Exception {
            final CountDownLatch start = new CountDownLatch(1);
            final Supplier<MvcTestResult> request = () -> {
                try {
                    start.await();
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return publish(List.of(101L, 102L, 103L), event(SITE_NAME, 80));
            };
            final List<MvcTestResult> results;
            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                final CompletableFuture<MvcTestResult> first = CompletableFuture.supplyAsync(request, executor);
                final CompletableFuture<MvcTestResult> second = CompletableFuture.supplyAsync(request, executor);
                start.countDown();
                results = List.of(first.get(), second.get());
            }

            assertThat(results)
                    .extracting(result -> result.getResponse().getStatus())
                    .containsExactlyInAnyOrder(HttpStatus.ACCEPTED.value(), HttpStatus.OK.value());
            assertThat(notificationIds(results.get(0))).isEqualTo(notificationIds(results.get(1)));
            assertThat(count("notifications")).isEqualTo(3);
            assertThat(count("notification_deliveries")).isEqualTo(3);
        }

        @Test
        @DisplayName("AC-16 a member added after an event was notified never receives it")
        void memberAddedLater() {
            publish(List.of(101L), event(SITE_NAME, 80));
            assertThat(post(
                            "/distribution-lists/1001/members",
                            JwtTokens.listAdmin(101),
                            "{\"address\": \"late@customer101.example\"}"))
                    .hasStatus(HttpStatus.CREATED);

            final MvcTestResult again = publish(List.of(101L), event(SITE_NAME, 80));

            assertThat(again).hasStatus(HttpStatus.OK);
            assertThat(jdbc.queryForList("SELECT address FROM notification_deliveries ORDER BY id", String.class))
                    .containsExactly("alice@customer101.example", "bob@customer101.example");
        }
    }

    @Nested
    class Rejected {

        @Test
        @DisplayName("AC-11 an unknown type is a 422 and nothing is stored")
        void unknownType() {
            final MvcTestResult result = post("/notifications", PUBLISHER, """
                    {"type": "SITE_CLOSED", "customerIds": [101], "parameters": %s}
                    """.formatted(event(SITE_NAME, 80)));

            assertUnknownType(result);
        }

        @Test
        @DisplayName("AC-11 an inactive type is a 422 and nothing is stored")
        void inactiveType() {
            jdbc.update("UPDATE notification_types SET active = false");
            try {
                assertUnknownType(publish(List.of(101L), event(SITE_NAME, 80)));
            } finally {
                jdbc.update("UPDATE notification_types SET active = true");
            }
        }

        @Test
        @DisplayName("AC-12 every schema violation is listed with a pointer into the body, and nothing is stored")
        void invalidParameters() {
            final MvcTestResult result = publish(List.of(101L), """
                    {"siteAddress": "Passeig de Gracia 19 Barcelona", "authorityName": "FDA",
                     "documentType": "LETTER", "issueDate": "2026-02-30", "confidence": 101, "unexpected": true}
                    """);

            assertProblem(result, HttpStatus.UNPROCESSABLE_CONTENT, "unprocessable-content");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.violations[*].pointer")
                    .asArray()
                    .containsExactlyInAnyOrder(
                            "/parameters/siteName",
                            "/parameters/unexpected",
                            "/parameters/documentType",
                            "/parameters/issueDate",
                            "/parameters/confidence");
            assertThat(count("notifications")).isZero();
        }

        @Test
        void whitespaceOnlyValuesAreValidatedAfterNormalization() {
            final MvcTestResult result = publish(List.of(101L), event(" \\t ", 80));

            assertProblem(result, HttpStatus.UNPROCESSABLE_CONTENT, "unprocessable-content");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.violations[*].pointer")
                    .asArray()
                    .containsExactly("/parameters/siteName");
        }

        private void assertUnknownType(final MvcTestResult result) {
            assertProblem(result, HttpStatus.UNPROCESSABLE_CONTENT, "unprocessable-content");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.violations[0].pointer")
                    .isEqualTo("/type");
            assertThat(count("notifications")).isZero();
        }
    }

    private MvcTestResult publish(final List<Long> customerIds, final String parameters) {
        return post("/notifications", PUBLISHER, """
                {"type": "REGULATORY_DOCUMENT_MATCHED", "customerIds": %s, "parameters": %s}
                """.formatted(customerIds, parameters));
    }

    private static String event(final String siteName, final int confidence) {
        return """
                {"siteName": "%s", "siteAddress": "Passeig de Gracia 19 Barcelona", "authorityName": "FDA",
                 "documentType": "WARNING_LETTER", "issueDate": "2026-06-12", "confidence": %d}
                """.formatted(siteName, confidence);
    }

    private static Tuple delivery(final long memberId, final String address) {
        return tuple(
                memberId,
                address,
                "FDA warning letter – Qualifyze Spain",
                BODY,
                "PENDING",
                0,
                "ACCEPTED",
                JwtTokens.PUBLISHER_CLIENT_ID,
                true);
    }

    private static String eventKeyOf(final MvcTestResult result) {
        return JsonPath.read(body(result), "$.eventKey");
    }

    private static List<Object> notificationIds(final MvcTestResult result) {
        final List<Map<String, Object>> results = JsonPath.read(body(result), "$.results");
        return results.stream().map(customer -> customer.get("notificationId")).toList();
    }

    private static String body(final MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (final UnsupportedEncodingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private int count(final String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
