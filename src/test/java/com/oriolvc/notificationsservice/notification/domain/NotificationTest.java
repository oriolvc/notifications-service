package com.oriolvc.notificationsservice.notification.domain;

import static com.oriolvc.notificationsservice.notification.domain.RegulatoryDocumentMatched.TYPE;
import static com.oriolvc.notificationsservice.notification.domain.RegulatoryDocumentMatched.sampleParameters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.tuple;

import com.oriolvc.notificationsservice.shared.domain.Channel;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final EventKey KEY = EventKey.of(TYPE.code(), sampleParameters());
    private static final List<Recipient> RECIPIENTS = List.of(
            new Recipient(1001, 1101, Channel.EMAIL, "alice@customer101.example"),
            new Recipient(1001, 1102, Channel.EMAIL, "bob@customer101.example"));

    @Test
    void createsAPendingDeliveryPerRecipientWithTheRenderedContent() {
        final Notification notification = Notification.create(TYPE, KEY, 101, sampleParameters(), RECIPIENTS, NOW);

        assertThat(notification.id()).isNull();
        assertThat(notification.notificationTypeId()).isEqualTo(TYPE.id());
        assertThat(notification.eventKey()).isEqualTo(KEY);
        assertThat(notification.customerId()).isEqualTo(101);
        assertThat(notification.outcome()).isEqualTo(NotificationOutcome.ACCEPTED);
        final RenderedMessage content = TemplateRenderer.render(
                RegulatoryDocumentMatched.EMAIL_TEMPLATE, TYPE.parametersSchema(), sampleParameters());
        assertThat(notification.deliveries())
                .extracting(
                        Delivery::distributionListId,
                        Delivery::memberId,
                        Delivery::channel,
                        Delivery::address,
                        Delivery::templateId,
                        Delivery::content,
                        Delivery::status,
                        Delivery::attemptCount,
                        Delivery::nextAttemptAt)
                .containsExactly(
                        tuple(
                                1001L,
                                1101L,
                                Channel.EMAIL,
                                "alice@customer101.example",
                                1L,
                                content,
                                DeliveryStatus.PENDING,
                                0,
                                NOW),
                        tuple(
                                1001L,
                                1102L,
                                Channel.EMAIL,
                                "bob@customer101.example",
                                1L,
                                content,
                                DeliveryStatus.PENDING,
                                0,
                                NOW));
    }

    @Test
    void withoutRecipientsTheOutcomeIsNoRecipients() {
        final Notification notification = Notification.create(TYPE, KEY, 103, sampleParameters(), List.of(), NOW);

        assertThat(notification.outcome()).isEqualTo(NotificationOutcome.NO_RECIPIENTS);
        assertThat(notification.deliveries()).isEmpty();
    }

    @Test
    void aRecipientOnAChannelWithoutTemplateIsAnError() {
        final NotificationType withoutTemplates =
                new NotificationType(TYPE.id(), TYPE.code(), TYPE.name(), true, TYPE.parametersSchema(), Map.of());

        assertThatIllegalStateException()
                .isThrownBy(() -> Notification.create(withoutTemplates, KEY, 101, sampleParameters(), RECIPIENTS, NOW))
                .withMessageContaining("no EMAIL template");
    }
}
