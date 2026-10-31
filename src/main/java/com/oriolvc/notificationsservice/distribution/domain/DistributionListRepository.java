package com.oriolvc.notificationsservice.distribution.domain;

import com.oriolvc.notificationsservice.shared.domain.Channel;
import java.util.List;
import java.util.Optional;

/** Every lookup is scoped by customer: a list of another tenant does not exist for the caller. */
public interface DistributionListRepository {

    /** Saves and flushes, so constraint violations and the new version surface inside the use case. */
    DistributionList saveAndFlush(DistributionList list);

    Optional<DistributionList> findByIdAndCustomerId(Long id, long customerId);

    List<DistributionList> findAllByCustomerIdOrderByIdAsc(long customerId);

    boolean existsByCustomerIdAndNotificationTypeIdAndChannelAndActiveTrue(
            long customerId, long notificationTypeId, Channel channel);

    boolean existsByCustomerIdAndName(long customerId, String name);
}
