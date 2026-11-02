package com.oriolvc.notificationsservice.notification.application.model;

public enum CustomerOutcome {
    ACCEPTED,
    NO_RECIPIENTS,
    /** The customer had already been notified of the event: nothing new was stored or sent. */
    DUPLICATE
}
