package com.oriolvc.notificationsservice.distribution.domain;

import java.util.List;
import java.util.Optional;

/** Every lookup is scoped by customer: a list of another tenant does not exist for the caller. */
public interface DistributionListRepository {

    /**
     * Saves and flushes, so the new version is in the response and unique-constraint violations surface as a
     * {@code DataIntegrityViolationException} (409).
     */
    DistributionList saveAndFlush(DistributionList list);

    Optional<DistributionList> findByIdAndCustomerId(Long id, long customerId);

    List<DistributionList> findAllByCustomerIdOrderByIdAsc(long customerId);
}
