package com.oriolvc.notificationsservice.distribution.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Queries on {@code distribution_lists}. Only {@link JpaDistributionListRepository} uses it. */
interface DistributionListDao extends JpaRepository<DistributionListDbo, Long> {

    Optional<DistributionListDbo> findByIdAndCustomerId(long id, long customerId);

    List<DistributionListDbo> findAllByCustomerIdOrderByIdAsc(long customerId);

    /** At most one per channel (A-13). */
    List<DistributionListDbo> findAllByCustomerIdAndNotificationTypeIdAndActiveTrue(
            long customerId, long notificationTypeId);
}
