package com.oriolvc.notificationsservice.distribution.application;

/** Self-service engagement metrics (RFC 13.1). */
public interface DistributionMetrics {

    /** A change customers made to their own configuration. */
    enum ConfigChange {
        LIST_CREATED,
        LIST_UPDATED,
        LIST_DEACTIVATED,
        MEMBER_ADDED,
        MEMBER_REMOVED
    }

    /** Recorded once the change is stored; a request that changes nothing is not counted. */
    void configChanged(ConfigChange change);
}
