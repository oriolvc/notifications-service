package com.oriolvc.notificationsservice.distribution.domain.exceptions;

import com.oriolvc.notificationsservice.distribution.domain.DistributionList;
import com.oriolvc.notificationsservice.shared.error.UnprocessableException;

/** A business limit rather than a conflict with another resource, so a 422 (RFC 6.2). */
public class MemberLimitReachedException extends UnprocessableException {

    public MemberLimitReachedException(final Long listId) {
        super("Distribution list " + listId + " already has " + DistributionList.MAX_ACTIVE_MEMBERS
                + " active members");
    }
}
