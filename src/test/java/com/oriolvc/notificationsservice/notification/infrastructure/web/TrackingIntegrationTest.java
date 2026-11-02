package com.oriolvc.notificationsservice.notification.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.oriolvc.notificationsservice.IntegrationTest;
import com.oriolvc.notificationsservice.JwtTokens;
import com.oriolvc.notificationsservice.notification.application.DispatchDueDeliveries;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * The read side (RFC 6.1). Customer 101's deliveries are sent on the second attempt, after an SMTP outage; customer
 * 102's are still pending a retry.
 */
@Sql("/fixtures/distribution-lists.sql")
class TrackingIntegrationTest extends IntegrationTest {

    private static final String PUBLISHER = JwtTokens.publisher();
    private static final String BODY =
            "Qualifyze Spain, located at Passeig de Gracia 19 Barcelona, received a FDA warning letter on 12 June 2026."
                    + " [80%]";

    @Autowired
    private DispatchDueDeliveries dispatch;

    @Autowired
    private JavaMailSenderImpl mailSender;

    private String eventKey;
    private long notification101;
    private long notification102;

    @BeforeEach
    void notifyWithOneRetry() throws Exception {
        final MvcTestResult created = post("/notifications", PUBLISHER, """
                {"type": "REGULATORY_DOCUMENT_MATCHED", "customerIds": [101, 102],
                 "parameters": {"siteName": "Qualifyze Spain", "siteAddress": "Passeig de Gracia 19 Barcelona",
                                "authorityName": "FDA", "documentType": "WARNING_LETTER",
                                "issueDate": "2026-06-12", "confidence": 80}}
                """);
        eventKey = JsonPath.read(body(created), "$.eventKey");
        notification101 = ((Number) JsonPath.read(body(created), "$.results[0].notificationId")).longValue();
        notification102 = ((Number) JsonPath.read(body(created), "$.results[1].notificationId")).longValue();

        try (ServerSocket socket = new ServerSocket(0)) {
            mailSender.setPort(socket.getLocalPort());
        }
        dispatch.execute();
        mailSender.setPort(greenMail.getSmtp().getPort());
        jdbc.update("""
                UPDATE notification_deliveries SET next_attempt_at = now() - interval '1 second'
                WHERE notification_id = ?
                """, notification101);
        dispatch.execute();
    }

    @AfterEach
    void reconnectToGreenMail() {
        mailSender.setPort(greenMail.getSmtp().getPort());
    }

    @Nested
    class Get {

        @Test
        @DisplayName("AC-4 recipients, timestamps, event key, status and every attempt")
        void tracksANotification() {
            final MvcTestResult result = get("/notifications/" + notification101, JwtTokens.viewer(101));

            assertThat(result).hasStatus(HttpStatus.OK);
            assertThat(result).bodyJson().isLenientlyEqualTo("""
                    {"id": %d, "type": "REGULATORY_DOCUMENT_MATCHED", "eventKey": "%s", "customerId": 101,
                     "outcome": "ACCEPTED", "createdBy": "detection-process",
                     "parameters": {"siteName": "Qualifyze Spain", "confidence": 80},
                     "deliveries": [
                       {"distributionListId": 1001, "memberId": 1101, "channel": "EMAIL",
                        "address": "alice@customer101.example", "subject": "FDA warning letter – Qualifyze Spain",
                        "body": "%s", "status": "SENT", "attemptCount": 2,
                        "attempts": [
                          {"attemptNumber": 1, "outcome": "RETRYABLE_ERROR", "errorCode": "CONNECTION"},
                          {"attemptNumber": 2, "outcome": "SUCCESS"}]},
                       {"memberId": 1102, "address": "bob@customer101.example", "status": "SENT"}]}
                    """.formatted(notification101, eventKey, BODY));
            assertThat(result).bodyJson().extractingPath("$.createdAt").isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.deliveries[0].sentAt")
                    .isNotNull();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.deliveries[0].providerMessageId")
                    .isEqualTo(JsonPath.read(body(result), "$.deliveries[0].attempts[1].providerMessageId"));
            assertThat(body(result)).doesNotContain("nextAttemptAt");
        }

        @Test
        void aPendingDeliveryShowsWhenItIsRetried() {
            final MvcTestResult result = get("/notifications/" + notification102, PUBLISHER);

            assertThat(result).hasStatus(HttpStatus.OK);
            assertThat(result).bodyJson().isLenientlyEqualTo("""
                    {"customerId": 102, "deliveries": [{"status": "PENDING", "attemptCount": 1}]}
                    """);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.deliveries[0].nextAttemptAt")
                    .isNotNull();
            assertThat(body(result)).doesNotContain("sentAt");
        }

        @Test
        @DisplayName("AC-18 another tenant's notification is not found")
        void anotherTenantsNotificationIsNotFound() {
            assertProblem(
                    get("/notifications/" + notification101, JwtTokens.viewer(102)), HttpStatus.NOT_FOUND, "not-found");
            assertThat(get("/notifications/" + notification101, JwtTokens.listAdmin(101)))
                    .hasStatus(HttpStatus.OK);
        }

        @Test
        void anUnknownNotificationIsNotFound() {
            assertProblem(get("/notifications/999999", PUBLISHER), HttpStatus.NOT_FOUND, "not-found");
        }
    }

    @Nested
    class Search {

        @Test
        void publishersSearchEveryTenantByEventReference() {
            final MvcTestResult result = get("/notifications?eventKey=" + eventKey, PUBLISHER);

            assertThat(result).hasStatus(HttpStatus.OK);
            assertThat(ids(result)).containsExactlyInAnyOrder(notification101, notification102);
            assertThat(JsonPath.<List<Object>>read(body(result), "$.items[*].type"))
                    .containsOnly("REGULATORY_DOCUMENT_MATCHED");
            assertThat(JsonPath.<List<Object>>read(body(result), "$.items[*].eventKey"))
                    .containsOnly(eventKey);
            assertThat(JsonPath.<List<Object>>read(body(result), "$.items[*].createdBy"))
                    .containsOnly("detection-process");
            assertThat(JsonPath.<List<Object>>read(body(result), "$.items[*].createdAt"))
                    .hasSize(2);
        }

        @Test
        void publishersCanFilterByCustomer() {
            assertThat(ids(get("/notifications?customerId=102", PUBLISHER))).containsExactly(notification102);
        }

        @Test
        void newestFirst() {
            final MvcTestResult later = post("/notifications", PUBLISHER, """
                    {"type": "REGULATORY_DOCUMENT_MATCHED", "customerIds": [101],
                     "parameters": {"siteName": "Qualifyze Spain", "siteAddress": "Passeig de Gracia 19 Barcelona",
                                    "authorityName": "FDA", "documentType": "OAI",
                                    "issueDate": "2026-07-01", "confidence": 90}}
                    """);
            final long newest = ((Number) JsonPath.read(body(later), "$.results[0].notificationId")).longValue();

            assertThat(ids(get("/notifications?customerId=101", PUBLISHER))).containsExactly(newest, notification101);
        }

        @Test
        void tenantUsersOnlySeeTheirOwnTenant() {
            assertThat(ids(get("/notifications", JwtTokens.viewer(101)))).containsExactly(notification101);
            assertThat(ids(get("/notifications?eventKey=" + eventKey, JwtTokens.listAdmin(102))))
                    .containsExactly(notification102);
        }

        @Test
        @DisplayName("AC-18 filtering by another tenant returns nothing")
        void anotherTenantIsAnEmptyResult() {
            final MvcTestResult result = get("/notifications?customerId=102", JwtTokens.viewer(101));

            assertThat(result).hasStatus(HttpStatus.OK);
            assertThat(ids(result)).isEmpty();
        }

        @Test
        void aMalformedEventKeyIsABadRequest() {
            assertProblem(get("/notifications?eventKey=abc", PUBLISHER), HttpStatus.BAD_REQUEST, "malformed-request");
        }
    }

    private static List<Long> ids(final MvcTestResult result) {
        final List<Number> ids = JsonPath.read(body(result), "$.items[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    private static String body(final MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (final UnsupportedEncodingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
