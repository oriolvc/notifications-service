package com.oriolvc.notificationsservice.distribution.domain;

import com.oriolvc.notificationsservice.shared.error.NotFoundException;

/** Also thrown for another tenant's list, so its existence is not revealed (RFC 5). */
public class DistributionListNotFoundException extends NotFoundException {

    public DistributionListNotFoundException(final long listId) {
        super("Distribution list " + listId + " not found");
    }
}
