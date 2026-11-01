package com.oriolvc.notificationsservice.distribution.infrastructure.channel.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oriolvc.notificationsservice.distribution.domain.InvalidAddressException;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailAddressValidatorTest {

    private final EmailAddressValidator validator = new EmailAddressValidator();

    @ParameterizedTest
    @ValueSource(
            strings = {
                "ana@example.com",
                "Ana.Lopez+alerts@mail.example.co.uk",
                "o'brien@example.ie",
                "x@a-b.example",
                "user_1{x}@sub.example.museum"
            })
    void acceptsCommonAddresses(final String address) {
        assertThat(validator.channel()).isEqualTo(Channel.EMAIL);
        assertThatCode(() -> validator.validate(address)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "ana",
                "ana@",
                "@example.com",
                "ana@example",
                "ana@@example.com",
                "ana lopez@example.com",
                ".ana@example.com",
                "ana.@example.com",
                "ana..lopez@example.com",
                "ana@-example.com",
                "ana@example-.com",
                "ana@example.c0m",
                "\"ana\"@example.com",
                "ana@[192.168.0.1]"
            })
    void rejectsInvalidAddresses(final String address) {
        assertThatThrownBy(() -> validator.validate(address)).isInstanceOf(InvalidAddressException.class);
    }

    @Test
    void rejectsTooLongAddressesAndNull() {
        assertThatThrownBy(() -> validator.validate(
                        "a".repeat(EmailAddressValidator.MAX_LOCAL_PART_LENGTH + 1) + "@example.com"))
                .isInstanceOf(InvalidAddressException.class);
        assertThatThrownBy(() -> validator.validate("ana@" + "b".repeat(EmailAddressValidator.MAX_LENGTH) + ".com"))
                .isInstanceOf(InvalidAddressException.class);
        assertThatThrownBy(() -> validator.validate(null)).isInstanceOf(InvalidAddressException.class);
    }
}
