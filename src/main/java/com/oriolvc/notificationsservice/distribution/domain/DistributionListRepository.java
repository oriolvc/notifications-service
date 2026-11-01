package com.oriolvc.notificationsservice.distribution.domain;

import java.util.List;
import java.util.Optional;

/** Every lookup is scoped by customer: a list of another tenant does not exist for the caller. */
public interface DistributionListRepository {

    /**
     * Saves the list and returns it as stored, with its id, new version and audit data. Writes immediately, so a
     * unique-constraint violation surfaces as a {@code DataIntegrityViolationException} (409) and a list changed since
     * it was read as an {@code ObjectOptimisticLockingFailureException} (409).
     */
    DistributionList save(DistributionList distributionList);

    Optional<DistributionList> findByIdAndCustomerId(long id, long customerId);

    List<DistributionList> findAllByCustomerIdOrderByIdAsc(long customerId);
}
