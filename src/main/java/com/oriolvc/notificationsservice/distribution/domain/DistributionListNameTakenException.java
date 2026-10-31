package com.oriolvc.notificationsservice.distribution.domain;

import com.oriolvc.notificationsservice.shared.error.AlreadyExistsException;

public class DistributionListNameTakenException extends AlreadyExistsException {

    public DistributionListNameTakenException(String name) {
        super("A distribution list named '" + name + "' already exists");
    }
}
