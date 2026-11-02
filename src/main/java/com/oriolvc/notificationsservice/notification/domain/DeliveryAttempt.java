package com.oriolvc.notificationsservice.notification.domain;

import java.time.Instant;

/** One send attempt of a delivery; append-only (RFC 9). */
public record DeliveryAttempt(
        int attemptNumber,
        Instant attemptedAt,
        AttemptOutcome outcome,
        String providerMessageId,
        String errorCode,
        String errorMessage) {}
