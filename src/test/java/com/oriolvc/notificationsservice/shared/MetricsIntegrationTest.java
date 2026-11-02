package com.oriolvc.notificationsservice.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.jayway.jsonpath.JsonPath;
import com.oriolvc.notificationsservice.IntegrationTest;
import com.oriolvc.notificationsservice.JwtTokens;
import com.oriolvc.notificationsservice.ScriptedSmtpServer;
import com.oriolvc.notificationsservice.notification.application.DispatchDueDeliveries;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * The metrics of RFC 13 after real requests and dispatch runs. The registry outlives each test (the context is
 * cached), so counters and summaries are compared with their value before the test.
 */
@Sql("/fixtures/distribution-lists.sql")
class MetricsIntegrationTest extends IntegrationTest {

    private static final String TYPE = "REGULATORY_DOCUMENT_MATCHED";
    private static final String PUBLISHER = JwtTokens.publisher();
    private static final String ADMIN_103 = JwtTokens.listAdmin(103);

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private DispatchDueDeliveries dispatch;

    @Autowired
    private JavaMailSenderImpl mailSender;

    @AfterEach
    void reconnectToGreenMail() {
        mailSender.setPort(greenMail.getSmtp().getPort());
    }

    @Test
    void aNotifyAndDispatchCycle() {
        final double accepted = counter("notifications", "outcome", "ACCEPTED");
        final double noRecipients = counter("notifications", "outcome", "NO_RECIPIENTS");
        final Snapshot recipients = summary("notification.recipients");
        final Snapshot reached = summary("notification.customers.reached.ratio");
        final double sent = counter("notification.deliveries", "status", "SENT");
        final long latencies = timerCount("notification.delivery.latency");
        final long sends = timerCount("notification.channel.send");
        final Snapshot attempts = summary("notification.delivery.attempts");

        // 101 has two recipients, 102 one, 103 no list
        publish("[101, 102, 103]", "WARNING_LETTER");

        assertThat(counter("notifications", "outcome", "ACCEPTED") - accepted).isEqualTo(2);
        assertThat(counter("notifications", "outcome", "NO_RECIPIENTS") - noRecipients)
                .isEqualTo(1);
        assertThat(summary("notification.recipients").minus(recipients)).isEqualTo(new Snapshot(2, 3));
        assertThat(summary("notification.customers.reached.ratio").count() - reached.count())
                .isEqualTo(1);
        assertThat(summary("notification.customers.reached.ratio").total() - reached.total())
                .isCloseTo(2.0 / 3, within(1e-9));
        assertThat(gauge("notification.deliveries.pending")).isEqualTo(3);
        assertThat(gauge("notification.deliveries.pending.oldest.age")).isNotNegative();

        assertThat(dispatch.execute()).isEqualTo(3);

        assertThat(counter("notification.deliveries", "status", "SENT") - sent).isEqualTo(3);
        assertThat(timerCount("notification.delivery.latency") - latencies).isEqualTo(3);
        assertThat(timerCount("notification.channel.send") - sends).isEqualTo(3);
        assertThat(summary("notification.delivery.attempts").minus(attempts)).isEqualTo(new Snapshot(3, 3));
        assertThat(gauge("notification.deliveries.pending")).isZero();
        assertThat(gauge("notification.deliveries.pending.oldest.age")).isZero();
    }

    @Test
    void aReplayedEventIsNotCountedAgain() {
        publish("[101]", "WARNING_LETTER");
        final double created = counter("notifications", "outcome", "ACCEPTED");
        final long reached = summary("notification.customers.reached.ratio").count();

        publish("[101]", "WARNING_LETTER");

        assertThat(counter("notifications", "outcome", "ACCEPTED")).isEqualTo(created);
        assertThat(summary("notification.customers.reached.ratio").count()).isEqualTo(reached);
    }

    @Test
    void failedDeliveriesAreCountedByErrorCodeAndRetriesAreNotCompletions() {
        final double failed = counter("notification.deliveries", "error_code", "SMTP_550");
        final long retried = timerCount("notification.channel.send", "outcome", "RETRYABLE_ERROR");
        final double completed = registry.find("notification.deliveries").counters().stream()
                .mapToDouble(Counter::count)
                .sum();
        publish("[102]", "OAI");

        try (ScriptedSmtpServer server = new ScriptedSmtpServer("451 4.3.0 Try again later")) {
            mailSender.setPort(server.port());
            dispatch.execute();
        }
        assertThat(timerCount("notification.channel.send", "outcome", "RETRYABLE_ERROR") - retried)
                .isEqualTo(1);
        assertThat(registry.find("notification.deliveries").counters().stream()
                        .mapToDouble(Counter::count)
                        .sum())
                .as("still pending")
                .isEqualTo(completed);

        jdbc.update("UPDATE notification_deliveries SET next_attempt_at = now() - interval '1 second'");
        try (ScriptedSmtpServer server = new ScriptedSmtpServer("550 5.1.1 Mailbox unknown")) {
            mailSender.setPort(server.port());
            dispatch.execute();
        }
        assertThat(counter("notification.deliveries", "error_code", "SMTP_550") - failed)
                .isEqualTo(1);
        assertThat(registry.get("notification.deliveries")
                        .tags("status", "FAILED", "error_code", "SMTP_550")
                        .counter()
                        .count())
                .isPositive();
    }

    @Test
    void selfServiceChangesAndActiveLists() {
        final double created = counter("distribution.config.changes", "operation", "LIST_CREATED");
        final double updated = counter("distribution.config.changes", "operation", "LIST_UPDATED");
        final double added = counter("distribution.config.changes", "operation", "MEMBER_ADDED");
        final double removed = counter("distribution.config.changes", "operation", "MEMBER_REMOVED");
        final double deactivated = counter("distribution.config.changes", "operation", "LIST_DEACTIVATED");
        assertThat(gauge("distribution.lists.active", "type", TYPE, "channel", "EMAIL"))
                .isEqualTo(2);

        final MvcTestResult list = post("/distribution-lists", ADMIN_103, """
                {"name": "Compliance", "notificationType": "REGULATORY_DOCUMENT_MATCHED", "channel": "EMAIL"}
                """);
        assertThat(list).hasStatus(HttpStatus.CREATED);
        final String uri = "/distribution-lists/" + JsonPath.read(body(list), "$.id");
        assertThat(gauge("distribution.lists.active", "type", TYPE, "channel", "EMAIL"))
                .isEqualTo(3);
        assertThat(patch(uri, ADMIN_103, "{\"name\": \"Compliance\", \"version\": 0}"))
                .as("changes nothing")
                .hasStatus(HttpStatus.OK);
        assertThat(patch(uri, ADMIN_103, "{\"name\": \"Quality\", \"version\": 0}"))
                .hasStatus(HttpStatus.OK);
        final MvcTestResult member = post(uri + "/members", ADMIN_103, "{\"address\": \"zoe@customer103.example\"}");
        assertThat(member).hasStatus(HttpStatus.CREATED);
        assertThat(delete(uri + "/members/" + JsonPath.read(body(member), "$.id"), ADMIN_103))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(delete(uri, ADMIN_103)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(delete(uri, ADMIN_103)).as("already inactive").hasStatus(HttpStatus.NO_CONTENT);

        assertThat(counter("distribution.config.changes", "operation", "LIST_CREATED") - created)
                .isEqualTo(1);
        assertThat(counter("distribution.config.changes", "operation", "LIST_UPDATED") - updated)
                .isEqualTo(1);
        assertThat(counter("distribution.config.changes", "operation", "MEMBER_ADDED") - added)
                .isEqualTo(1);
        assertThat(counter("distribution.config.changes", "operation", "MEMBER_REMOVED") - removed)
                .isEqualTo(1);
        assertThat(counter("distribution.config.changes", "operation", "LIST_DEACTIVATED") - deactivated)
                .isEqualTo(1);
        assertThat(gauge("distribution.lists.active", "type", TYPE, "channel", "EMAIL"))
                .isEqualTo(2);
    }

    @Test
    void publishersScrapeThePrometheusEndpoint() {
        publish("[101]", "WARNING_LETTER");

        final MvcTestResult scrape = get("/actuator/prometheus", PUBLISHER);

        assertThat(scrape).hasStatus(HttpStatus.OK);
        assertThat(body(scrape))
                .contains(
                        "notifications_total{",
                        "notification_customers_reached_ratio_count{",
                        "notification_deliveries_pending ",
                        "notification_deliveries_pending_oldest_age_seconds ",
                        "distribution_lists_active{",
                        "http_server_requests_seconds_bucket{")
                .doesNotContain("customer101.example");
    }

    @Test
    void onlyPublishersMayScrape() {
        assertProblem(get("/actuator/prometheus", JwtTokens.viewer(101)), HttpStatus.FORBIDDEN, "forbidden");
        assertThat(mvc.get().uri("/actuator/prometheus").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private void publish(final String customerIds, final String documentType) {
        final MvcTestResult result = post("/notifications", PUBLISHER, """
                {"type": "REGULATORY_DOCUMENT_MATCHED", "customerIds": %s,
                 "parameters": {"siteName": "Qualifyze Spain", "siteAddress": "Passeig de Gracia 19 Barcelona",
                                "authorityName": "FDA", "documentType": "%s",
                                "issueDate": "2026-06-12", "confidence": 80}}
                """.formatted(customerIds, documentType));
        assertThat(result.getResponse().getStatus()).isIn(200, 202);
    }

    private double counter(final String name, final String... tags) {
        return registry.find(name).tags(tags).counters().stream()
                .mapToDouble(Counter::count)
                .sum();
    }

    private long timerCount(final String name, final String... tags) {
        return registry.find(name).tags(tags).timers().stream()
                .mapToLong(Timer::count)
                .sum();
    }

    private Snapshot summary(final String name) {
        return registry.find(name).summaries().stream()
                .map(summary -> new Snapshot(summary.count(), summary.totalAmount()))
                .reduce(new Snapshot(0, 0), Snapshot::plus);
    }

    private double gauge(final String name, final String... tags) {
        final Gauge gauge = registry.get(name).tags(tags).gauge();
        return gauge.value();
    }

    private static String body(final MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (final UnsupportedEncodingException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A distribution summary's count and total. */
    private record Snapshot(long count, double total) {

        Snapshot plus(final Snapshot other) {
            return new Snapshot(count + other.count, total + other.total);
        }

        Snapshot minus(final Snapshot other) {
            return new Snapshot(count - other.count, total - other.total);
        }
    }
}
