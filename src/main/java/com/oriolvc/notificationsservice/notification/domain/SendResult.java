package com.oriolvc.notificationsservice.notification.domain;

import java.util.Objects;

/**
 * What a channel reported for one send.
 *
 * @param providerMessageId the provider's id of the message (e.g. the email Message-ID); only on success
 * @param errorCode a short, stable code (e.g. {@code SMTP_550}, {@code CONNECTION}); only on errors
 * @param errorMessage the provider's message, for the audit trail; only on errors
 */
public record SendResult(AttemptOutcome outcome, String providerMessageId, String errorCode, String errorMessage) {

    public SendResult {
        Objects.requireNonNull(outcome, "outcome");
    }

    public static SendResult success(final String providerMessageId) {
        return new SendResult(AttemptOutcome.SUCCESS, providerMessageId, null, null);
    }

    public static SendResult retryable(final String errorCode, final String errorMessage) {
        return new SendResult(AttemptOutcome.RETRYABLE_ERROR, null, errorCode, errorMessage);
    }

    public static SendResult permanent(final String errorCode, final String errorMessage) {
        return new SendResult(AttemptOutcome.PERMANENT_ERROR, null, errorCode, errorMessage);
    }
}
