package com.oriolvc.notificationsservice.notification.domain;

/** What a stored notification led to. A duplicate is not a stored outcome: nothing new is stored for it. */
public enum NotificationOutcome {
    /** At least one delivery was created. */
    ACCEPTED,
    /** The customer has no active list for the type, or its lists have no active members. Kept for the audit trail. */
    NO_RECIPIENTS
}
