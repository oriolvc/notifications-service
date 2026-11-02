package com.oriolvc.notificationsservice.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.oriolvc.notificationsservice.shared.domain.Channel;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeliveryTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    private final Delivery delivery = Delivery.pending(
            new Recipient(1001, 1101, Channel.EMAIL, "alice@customer101.example"),
            RegulatoryDocumentMatched.EMAIL_TEMPLATE,
            new RenderedMessage("subject", "body"),
            NOW);

    @Test
    void successMarksItSent() {
        delivery.recordAttempt(SendResult.success("<id@smtp>"), NOW);

        assertThat(delivery.status()).isEqualTo(DeliveryStatus.SENT);
        assertThat(delivery.sentAt()).isEqualTo(NOW);
        assertThat(delivery.providerMessageId()).isEqualTo("<id@smtp>");
        assertThat(delivery.attempts())
                .containsExactly(new DeliveryAttempt(1, NOW, AttemptOutcome.SUCCESS, "<id@smtp>", null, null));
    }

    @Test
    void retryableErrorsBackOffThenFailAfterFiveAttempts() {
        Instant at = NOW;
        for (final Duration backoff :
                List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofMinutes(60))) {
            delivery.recordAttempt(SendResult.retryable("CONNECTION", "refused"), at);

            assertThat(delivery.status()).isEqualTo(DeliveryStatus.PENDING);
            assertThat(delivery.nextAttemptAt()).isEqualTo(at.plus(backoff));
            at = delivery.nextAttemptAt();
        }

        delivery.recordAttempt(SendResult.retryable("CONNECTION", "refused"), at);

        assertThat(delivery.status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.attemptCount()).isEqualTo(Delivery.MAX_ATTEMPTS);
        assertThat(delivery.attempts())
                .extracting(DeliveryAttempt::attemptNumber)
                .containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void aPermanentErrorFailsItImmediately() {
        delivery.recordAttempt(SendResult.permanent("SMTP_550", "Mailbox unknown"), NOW);

        assertThat(delivery.status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.sentAt()).isNull();
        assertThat(delivery.attempts())
                .containsExactly(new DeliveryAttempt(
                        1, NOW, AttemptOutcome.PERMANENT_ERROR, null, "SMTP_550", "Mailbox unknown"));
    }

    @Test
    void onlyPendingDeliveriesCanBeAttempted() {
        delivery.recordAttempt(SendResult.success("<id@smtp>"), NOW);

        assertThatIllegalStateException()
                .isThrownBy(() -> delivery.recordAttempt(SendResult.success("<again@smtp>"), NOW));
    }
}
