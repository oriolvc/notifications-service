package com.oriolvc.notificationsservice.distribution.domain;

import com.oriolvc.notificationsservice.shared.domain.Channel;
import java.util.List;
import java.util.Optional;

/** Every lookup is scoped by customer: a list of another tenant does not exist for the caller. */
public interface DistributionListRepository {

    /**
     * Saves the list and its members and returns it as stored, with ids, new version and audit data. A change to the
     * members also bumps the list's version. Writes immediately, so a unique-constraint violation surfaces as a
     * {@code DataIntegrityViolationException} (409) and a list changed since it was read as an
     * {@code ObjectOptimisticLockingFailureException} (409).
     */
    DistributionList save(DistributionList distributionList);

    Optional<DistributionList> findByIdAndCustomerId(long id, long customerId);

    List<DistributionList> findAllByCustomerIdOrderByIdAsc(long customerId);

    /** The customer's active lists for a notification type: at most one per channel (A-13). */
    List<DistributionList> findActiveByCustomerIdAndNotificationTypeId(long customerId, long notificationTypeId);

    /** Active lists of every customer for a notification type and channel. */
    long countActive(long notificationTypeId, Channel channel);
}
