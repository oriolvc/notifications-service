package com.oriolvc.notificationsservice.distribution.domain.exceptions;

import com.oriolvc.notificationsservice.shared.domain.Channel;
import com.oriolvc.notificationsservice.shared.error.MalformedRequestException;
import com.oriolvc.notificationsservice.shared.error.Violation;
import java.util.List;

/** AC-19: an address that is not valid for the list's channel is a 400. The address itself is not echoed. */
public class InvalidAddressException extends MalformedRequestException {

    public InvalidAddressException(final Channel channel) {
        super(
                "The address is not valid for the " + channel + " channel",
                List.of(new Violation("/address", "must be a valid " + channel + " address")));
    }
}
