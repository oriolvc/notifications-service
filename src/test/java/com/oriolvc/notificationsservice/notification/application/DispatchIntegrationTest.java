package com.oriolvc.notificationsservice.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.oriolvc.notificationsservice.IntegrationTest;
import com.oriolvc.notificationsservice.JwtTokens;
import com.oriolvc.notificationsservice.ScriptedSmtpServer;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.jdbc.Sql;

/** From an accepted event to the SMTP server (GreenMail), with outages and rejections simulated (RFC 9). */
@Sql("/fixtures/distribution-lists.sql")
class DispatchIntegrationTest extends IntegrationTest {

    private static final String BODY =
            "Qualifyze Spain, located at Passeig de Gracia 19 Barcelona, received a FDA warning letter on 12 June 2026."
                    + " [80%]";

    @Autowired
    private DispatchDueDeliveries dispatch;

    @Autowired
    private JavaMailSenderImpl mailSender;

    @AfterEach
    void reconnectToGreenMail() {
        mailSender.setPort(greenMail.getSmtp().getPort());
    }

    @Test
    @DisplayName("AC-1 every member receives exactly one email with the rendered body, and every delivery is SENT")
    void notifiesAffectedCustomers() throws Exception {
        jdbc.update("""
                INSERT INTO distribution_list_members (id, distribution_list_id, address, active, version,
                                                       created_at, created_by, updated_at, updated_by)
                VALUES (2102, 2001, 'erin@customer102.example', true, 0, now(), 'test', now(), 'test')
                """);
        publish(101, 102);
        assertThat(count("SELECT count(*) FROM notification_deliveries WHERE status = 'PENDING'"))
                .isEqualTo(4);

        assertThat(dispatch.execute()).isEqualTo(4);

        final MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received)
                .extracting(message -> message.getAllRecipients()[0].toString())
                .containsExactlyInAnyOrder(
                        "alice@customer101.example",
                        "bob@customer101.example",
                        "dave@customer102.example",
                        "erin@customer102.example");
        for (final MimeMessage message : received) {
            assertThat(message.getAllRecipients()).hasSize(1);
            assertThat(message.getSubject()).isEqualTo("FDA warning letter – Qualifyze Spain");
            assertThat(content(message)).isEqualTo(BODY);
        }
        assertThat(jdbc.queryForList("""
                        SELECT d.status, d.attempt_count, d.sent_at IS NOT NULL AS sent,
                               d.provider_message_id = a.provider_message_id AS same_id, a.outcome
                        FROM notification_deliveries d JOIN delivery_attempts a ON a.delivery_id = d.id
                        """))
                .extracting(
                        row -> row.get("status"),
                        row -> row.get("attempt_count"),
                        row -> row.get("sent"),
                        row -> row.get("same_id"),
                        row -> row.get("outcome"))
                .containsOnly(tuple("SENT", 1, true, true, "SUCCESS"))
                .hasSize(4);
        assertThat(jdbc.queryForList("SELECT provider_message_id FROM notification_deliveries", String.class))
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(received)
                        .map(DispatchIntegrationTest::messageId)
                        .toList());
        assertThat(dispatch.execute()).as("nothing left to send").isZero();
    }

    @Test
    @DisplayName("AC-13 while SMTP is unavailable the delivery stays PENDING, one RETRYABLE_ERROR per try, then SENT")
    void smtpOutageThenRecovery() {
        publish(102);
        mailSender.setPort(closedPort());

        assertThat(dispatch.execute()).isEqualTo(1);
        assertThat(dispatch.execute()).as("not due before its backoff").isZero();
        makeDue();
        assertThat(dispatch.execute()).isEqualTo(1);

        assertThat(deliveryState()).containsEntry("status", "PENDING").containsEntry("attempt_count", 2);
        assertThat(count("""
                        SELECT count(*) FROM notification_deliveries
                        WHERE next_attempt_at BETWEEN now() + interval '4 minutes' AND now() + interval '6 minutes'
                        """)).as("second backoff is 5 minutes").isEqualTo(1);

        reconnectToGreenMail();
        makeDue();
        assertThat(dispatch.execute()).isEqualTo(1);

        assertThat(deliveryState()).containsEntry("status", "SENT").containsEntry("attempt_count", 3);
        assertThat(attempts())
                .containsExactly(
                        tuple(1, "RETRYABLE_ERROR", "CONNECTION"),
                        tuple(2, "RETRYABLE_ERROR", "CONNECTION"),
                        tuple(3, "SUCCESS", null));
        assertThat(greenMail.getReceivedMessages()).hasSize(1);
    }

    @Test
    @DisplayName("AC-14 SMTP that keeps failing: FAILED after 5 attempts, every attempt stored")
    void failsAfterFiveAttempts() {
        publish(102);
        try (ScriptedSmtpServer server = new ScriptedSmtpServer("451 4.3.0 Try again later")) {
            mailSender.setPort(server.port());
            for (int attempt = 1; attempt <= 5; attempt++) {
                makeDue();
                assertThat(dispatch.execute()).isEqualTo(1);
            }
            makeDue();
            assertThat(dispatch.execute())
                    .as("a failed delivery is not retried")
                    .isZero();
        }

        assertThat(deliveryState()).containsEntry("status", "FAILED").containsEntry("attempt_count", 5);
        assertThat(attempts())
                .extracting(attempt -> attempt.toList().get(1))
                .containsExactly(
                        "RETRYABLE_ERROR", "RETRYABLE_ERROR", "RETRYABLE_ERROR", "RETRYABLE_ERROR", "RETRYABLE_ERROR");
    }

    @Test
    @DisplayName("AC-14 an SMTP 5xx fails the delivery immediately")
    void permanentErrorFailsImmediately() {
        publish(102);
        try (ScriptedSmtpServer server = new ScriptedSmtpServer("550 5.1.1 Mailbox unknown")) {
            mailSender.setPort(server.port());
            assertThat(dispatch.execute()).isEqualTo(1);
        }

        assertThat(deliveryState()).containsEntry("status", "FAILED").containsEntry("attempt_count", 1);
        assertThat(attempts()).containsExactly(tuple(1, "PERMANENT_ERROR", "SMTP_550"));
    }

    @Test
    @DisplayName("AC-15 a member removed after acceptance still gets the email, at the snapshot address")
    void memberRemovedBeforeDispatch() throws Exception {
        publish(102);
        assertThat(delete("/distribution-lists/2001/members/2101", JwtTokens.listAdmin(102)))
                .hasStatus(HttpStatus.NO_CONTENT);

        dispatch.execute();

        assertThat(greenMail.getReceivedMessages())
                .singleElement()
                .satisfies(message ->
                        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("dave@customer102.example"));
        assertThat(deliveryState()).containsEntry("status", "SENT");
    }

    @Test
    void concurrentDispatchersSendEachDeliveryOnce() throws Exception {
        publish(101, 102);
        final CountDownLatch start = new CountDownLatch(1);
        final Supplier<Integer> run = () -> {
            try {
                start.await();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return dispatch.execute();
        };
        final List<Integer> attempted;
        try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
            final List<CompletableFuture<Integer>> runs = List.of(
                    CompletableFuture.supplyAsync(run, executor),
                    CompletableFuture.supplyAsync(run, executor),
                    CompletableFuture.supplyAsync(run, executor));
            start.countDown();
            attempted = runs.stream().map(CompletableFuture::join).toList();
        }

        assertThat(attempted.stream().mapToInt(Integer::intValue).sum()).isEqualTo(3);
        assertThat(greenMail.getReceivedMessages()).hasSize(3);
        assertThat(count("SELECT count(*) FROM delivery_attempts")).isEqualTo(3);
    }

    private void publish(final long... customerIds) {
        assertThat(post("/notifications", JwtTokens.publisher(), """
                        {"type": "REGULATORY_DOCUMENT_MATCHED", "customerIds": %s,
                         "parameters": {"siteName": "Qualifyze Spain", "siteAddress": "Passeig de Gracia 19 Barcelona",
                                        "authorityName": "FDA", "documentType": "WARNING_LETTER",
                                        "issueDate": "2026-06-12", "confidence": 80}}
                        """.formatted(Arrays.toString(customerIds))))
                .hasStatus(HttpStatus.ACCEPTED);
    }

    /** Moves time forward for pending deliveries rather than the clock. */
    private void makeDue() {
        jdbc.update("UPDATE notification_deliveries SET next_attempt_at = now() - interval '1 second'"
                + " WHERE status = 'PENDING'");
    }

    private Map<String, Object> deliveryState() {
        return jdbc.queryForMap("SELECT status, attempt_count FROM notification_deliveries");
    }

    private List<Tuple> attempts() {
        return jdbc.query(
                "SELECT attempt_number, outcome, error_code FROM delivery_attempts ORDER BY attempt_number",
                (row, index) ->
                        tuple(row.getInt("attempt_number"), row.getString("outcome"), row.getString("error_code")));
    }

    private int count(final String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private static int closedPort() {
        try (ScriptedSmtpServer server = new ScriptedSmtpServer("250 OK")) {
            return server.port();
        }
    }

    private static String content(final MimeMessage message) throws MessagingException, IOException {
        return ((String) message.getContent()).strip();
    }

    private static String messageId(final MimeMessage message) {
        try {
            return message.getMessageID();
        } catch (final MessagingException e) {
            throw new IllegalStateException(e);
        }
    }
}
