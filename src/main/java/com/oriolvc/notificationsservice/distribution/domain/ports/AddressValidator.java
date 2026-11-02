package com.oriolvc.notificationsservice.distribution.domain.ports;

import com.oriolvc.notificationsservice.distribution.domain.exceptions.InvalidAddressException;
import com.oriolvc.notificationsservice.shared.domain.Channel;

/** Checks that an address can receive messages on one channel (an email address for {@code EMAIL}). */
public interface AddressValidator {

    Channel channel();

    /** @throws InvalidAddressException if the address is not valid for {@link #channel()} */
    void validate(String address);
}
