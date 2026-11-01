package com.oriolvc.notificationsservice.distribution.domain;

import com.oriolvc.notificationsservice.shared.error.AlreadyExistsException;

/** AC-19: the address is already an active member of the list (compared ignoring case). */
public class MemberAlreadyActiveException extends AlreadyExistsException {

    public MemberAlreadyActiveException(final long listId, final long memberId) {
        super("The address is already active in distribution list " + listId + " as member " + memberId);
    }
}
