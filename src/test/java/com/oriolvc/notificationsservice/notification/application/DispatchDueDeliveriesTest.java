package com.oriolvc.notificationsservice.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.oriolvc.notificationsservice.notification.domain.Delivery;
import com.oriolvc.notificationsservice.notification.domain.DeliveryRepository;
import com.oriolvc.notificationsservice.notification.domain.DeliveryStatus;
import com.oriolvc.notificationsservice.notification.domain.RenderedMessage;
import com.oriolvc.notificationsservice.notification.domain.SendResult;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class DispatchDueDeliveriesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void attemptsEveryDueDeliveryWithTheSenderOfItsChannel() {
        final FakeDeliveries deliveries = new FakeDeliveries(delivery(1), delivery(2));
        final DispatchDueDeliveries dispatch =
                new DispatchDueDeliveries(deliveries, senders(delivery -> SendResult.success("<m>")), CLOCK);

        assertThat(dispatch.execute()).isEqualTo(2);
        assertThat(deliveries.attempted)
                .extracting(Delivery::status)
                .containsExactly(DeliveryStatus.SENT, DeliveryStatus.SENT);
    }

    @Test
    void stopsTheRunWhenADeliveryCannotBeAttempted() {
        final FakeDeliveries deliveries = new FakeDeliveries(delivery(1), delivery(2));
        final DispatchDueDeliveries dispatch = new DispatchDueDeliveries(
                deliveries,
                senders(delivery -> {
                    throw new IllegalStateException("bug in a sender");
                }),
                CLOCK);

        assertThat(dispatch.execute()).isZero();
        assertThat(deliveries.queue).hasSize(2);
    }

    @Test
    void eachChannelHasExactlyOneSender() {
        final ChannelSender email = sender(delivery -> SendResult.success("<m>"));

        assertThatIllegalStateException()
                .isThrownBy(() -> new ChannelSenders(List.of(email, email)))
                .withMessageContaining("Two channel senders");
        assertThatIllegalStateException()
                .isThrownBy(() -> new ChannelSenders(List.of()).forChannel(Channel.EMAIL))
                .withMessageContaining("No channel sender");
    }

    private static ChannelSenders senders(final Function<Delivery, SendResult> send) {
        return new ChannelSenders(List.of(sender(send)));
    }

    private static ChannelSender sender(final Function<Delivery, SendResult> send) {
        return new ChannelSender() {
            @Override
            public Channel channel() {
                return Channel.EMAIL;
            }

            @Override
            public SendResult send(final Delivery delivery) {
                return send.apply(delivery);
            }
        };
    }

    private static Delivery delivery(final long id) {
        return Delivery.restore(
                id,
                1001,
                1100 + id,
                Channel.EMAIL,
                "member" + id + "@customer101.example",
                1,
                new RenderedMessage("subject", "body"),
                DeliveryStatus.PENDING,
                CLOCK.instant(),
                null,
                null,
                List.of());
    }

    /** Hands out the queued deliveries in order; one that fails to be attempted stays queued, like a rollback. */
    private static final class FakeDeliveries implements DeliveryRepository {

        private final Deque<Delivery> queue;
        private final List<Delivery> attempted = new ArrayList<>();

        FakeDeliveries(final Delivery... deliveries) {
            queue = new ArrayDeque<>(List.of(deliveries));
        }

        @Override
        public boolean attemptNextDue(final Instant now, final Consumer<Delivery> attempt) {
            final Delivery next = queue.peekFirst();
            if (next == null) {
                return false;
            }
            attempt.accept(next);
            attempted.add(queue.removeFirst());
            return true;
        }
    }
}
