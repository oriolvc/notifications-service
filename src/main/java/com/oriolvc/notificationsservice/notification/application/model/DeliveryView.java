package com.oriolvc.notificationsservice.notification.application.model;

import com.oriolvc.notificationsservice.notification.domain.DeliveryAttempt;
import com.oriolvc.notificationsservice.notification.domain.DeliveryStatus;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import java.time.Instant;
import java.util.List;

/**
 * @param subject {@code null} for channels without one
 * @param nextAttemptAt only while {@code PENDING}
 * @param sentAt only once {@code SENT}
 * @param attempts oldest first
 */
public record DeliveryView(
        long id,
        long distributionListId,
        long memberId,
        Channel channel,
        String address,
        String subject,
        String body,
        DeliveryStatus status,
        int attemptCount,
        Instant nextAttemptAt,
        Instant sentAt,
        String providerMessageId,
        List<DeliveryAttempt> attempts) {

    public DeliveryView {
        attempts = List.copyOf(attempts);
    }
}
