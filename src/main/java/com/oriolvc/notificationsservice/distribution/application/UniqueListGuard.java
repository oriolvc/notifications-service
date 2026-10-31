package com.oriolvc.notificationsservice.distribution.application;

import com.oriolvc.notificationsservice.distribution.domain.ActiveDistributionListExistsException;
import com.oriolvc.notificationsservice.distribution.domain.DistributionList;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListNameTakenException;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Saves a list and turns a unique-constraint violation into the matching 409. The use cases check first; this covers
 * the race where two requests pass the check at the same time and the database decides.
 */
@Component
class UniqueListGuard {

    static final String ACTIVE_LIST_INDEX = "uq_distribution_lists_active_customer_type_channel";
    static final String NAME_CONSTRAINT = "uq_distribution_lists_customer_name";

    private final DistributionListRepository lists;

    UniqueListGuard(DistributionListRepository lists) {
        this.lists = lists;
    }

    DistributionList saveAndFlush(DistributionList list, String notificationTypeCode) {
        try {
            return lists.saveAndFlush(list);
        } catch (DataIntegrityViolationException e) {
            String message = String.valueOf(e.getMostSpecificCause().getMessage());
            if (message.contains(ACTIVE_LIST_INDEX)) {
                throw new ActiveDistributionListExistsException(notificationTypeCode, list.channel());
            }
            if (message.contains(NAME_CONSTRAINT)) {
                throw new DistributionListNameTakenException(list.name());
            }
            throw e;
        }
    }
}
