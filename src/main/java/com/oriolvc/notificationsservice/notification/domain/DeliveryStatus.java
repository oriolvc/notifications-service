package com.oriolvc.notificationsservice.notification.domain;

public enum DeliveryStatus {
    /** Waiting for its next attempt; the dispatcher's work queue. */
    PENDING,
    SENT,
    FAILED
}
