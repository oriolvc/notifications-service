package com.oriolvc.notificationsservice.notification.application.model;

import java.util.List;

/** @param results one per customer, in the order of the command */
public record CreateNotificationResult(String eventKey, List<CustomerResult> results) {

    public CreateNotificationResult {
        results = List.copyOf(results);
    }

    /** Whether at least one new notification was stored (202), rather than all duplicates (200). */
    public boolean anyCreated() {
        return results.stream().anyMatch(result -> result.outcome() != CustomerOutcome.DUPLICATE);
    }
}
