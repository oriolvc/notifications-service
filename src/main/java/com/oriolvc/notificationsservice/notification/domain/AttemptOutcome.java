package com.oriolvc.notificationsservice.notification.domain;

public enum AttemptOutcome {
    SUCCESS,
    /** Worth retrying: connection error, timeout, SMTP 4xx. */
    RETRYABLE_ERROR,
    /** Retrying cannot help: SMTP 5xx (e.g. unknown mailbox), an unusable address. */
    PERMANENT_ERROR
}
