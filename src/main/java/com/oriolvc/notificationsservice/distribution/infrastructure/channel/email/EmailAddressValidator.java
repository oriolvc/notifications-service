package com.oriolvc.notificationsservice.distribution.infrastructure.channel.email;

import com.oriolvc.notificationsservice.distribution.domain.exceptions.InvalidAddressException;
import com.oriolvc.notificationsservice.distribution.domain.ports.AddressValidator;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * A pragmatic subset of RFC 5321/5322: a dot-atom local part (at most 64 characters) and a domain name with at least
 * two labels and an alphabetic top-level domain, 254 characters in total. Quoted local parts and IP-literal domains
 * are rejected: they are valid but almost never used, and most mail providers refuse them anyway.
 */
@Component
class EmailAddressValidator implements AddressValidator {

    static final int MAX_LENGTH = 254;
    static final int MAX_LOCAL_PART_LENGTH = 64;

    private static final String ATOM = "[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+";
    private static final String LABEL = "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?";
    private static final Pattern EMAIL =
            Pattern.compile("^(" + ATOM + "(?:\\." + ATOM + ")*)@((?:" + LABEL + "\\.)+[A-Za-z]{2,63})$");

    @Override
    public Channel channel() {
        return Channel.EMAIL;
    }

    @Override
    public void validate(final String address) {
        if (address == null || address.length() > MAX_LENGTH) {
            throw new InvalidAddressException(channel());
        }
        final var matcher = EMAIL.matcher(address);
        if (!matcher.matches() || matcher.group(1).length() > MAX_LOCAL_PART_LENGTH) {
            throw new InvalidAddressException(channel());
        }
    }
}
