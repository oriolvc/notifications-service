package com.oriolvc.notificationsservice.notification.application.model;

/** @param deliveries number of deliveries of the new notification, or of the existing one for a duplicate */
public record CustomerResult(long customerId, long notificationId, CustomerOutcome outcome, int deliveries) {}
