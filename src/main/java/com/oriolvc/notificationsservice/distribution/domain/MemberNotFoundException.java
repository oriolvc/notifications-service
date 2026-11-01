package com.oriolvc.notificationsservice.distribution.domain;

import com.oriolvc.notificationsservice.shared.error.NotFoundException;

/** The member does not exist in this list (it may exist in another list or tenant). */
public class MemberNotFoundException extends NotFoundException {

    public MemberNotFoundException(final Long listId, final long memberId) {
        super("Member " + memberId + " not found in distribution list " + listId);
    }
}
