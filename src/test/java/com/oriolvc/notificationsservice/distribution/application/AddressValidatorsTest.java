package com.oriolvc.notificationsservice.distribution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oriolvc.notificationsservice.distribution.domain.AddressValidator;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import java.util.List;
import org.junit.jupiter.api.Test;

class AddressValidatorsTest {

    private static final AddressValidator EMAIL = new AddressValidator() {
        @Override
        public Channel channel() {
            return Channel.EMAIL;
        }

        @Override
        public void validate(final String address) {}
    };

    @Test
    void selectsTheValidatorOfTheChannel() {
        assertThat(new AddressValidators(List.of(EMAIL)).forChannel(Channel.EMAIL))
                .isSameAs(EMAIL);
    }

    @Test
    void everyChannelNeedsExactlyOneValidator() {
        assertThatThrownBy(() -> new AddressValidators(List.of()).forChannel(Channel.EMAIL))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AddressValidators(List.of(EMAIL, EMAIL)))
                .isInstanceOf(IllegalStateException.class);
    }
}
