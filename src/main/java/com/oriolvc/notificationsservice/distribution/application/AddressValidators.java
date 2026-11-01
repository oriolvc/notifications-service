package com.oriolvc.notificationsservice.distribution.application;

import com.oriolvc.notificationsservice.distribution.domain.AddressValidator;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** The {@link AddressValidator} of each channel. Adding a channel means adding a validator bean (RFC 4). */
@Component
class AddressValidators {

    private final Map<Channel, AddressValidator> byChannel = new EnumMap<>(Channel.class);

    AddressValidators(final List<AddressValidator> validators) {
        for (final AddressValidator validator : validators) {
            if (byChannel.put(validator.channel(), validator) != null) {
                throw new IllegalStateException("Two address validators for " + validator.channel());
            }
        }
    }

    AddressValidator forChannel(final Channel channel) {
        final AddressValidator validator = byChannel.get(channel);
        if (validator == null) {
            throw new IllegalStateException("No address validator for " + channel);
        }
        return validator;
    }
}
