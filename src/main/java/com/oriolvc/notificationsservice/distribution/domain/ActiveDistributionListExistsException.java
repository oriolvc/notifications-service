package com.oriolvc.notificationsservice.distribution.domain;

import com.oriolvc.notificationsservice.shared.domain.Channel;
import com.oriolvc.notificationsservice.shared.error.AlreadyExistsException;

/** A-13: one active list per (customer, notification type, channel). */
public class ActiveDistributionListExistsException extends AlreadyExistsException {

    public ActiveDistributionListExistsException(String notificationType, Channel channel) {
        super("There is already an active " + channel + " list for " + notificationType);
    }
}
