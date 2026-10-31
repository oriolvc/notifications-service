package com.oriolvc.notificationsservice.distribution.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oriolvc.notificationsservice.shared.domain.Channel;
import com.oriolvc.notificationsservice.shared.error.VersionConflictException;
import org.junit.jupiter.api.Test;

class DistributionListTest {

    @Test
    void createsAnActiveListWithATrimmedName() {
        DistributionList list = DistributionList.create(101, "  Compliance  ", 1, Channel.EMAIL);

        assertThat(list.isActive()).isTrue();
        assertThat(list.name()).isEqualTo("Compliance");
        assertThat(list.customerId()).isEqualTo(101);
        assertThat(list.notificationTypeId()).isEqualTo(1);
        assertThat(list.channel()).isEqualTo(Channel.EMAIL);
        assertThat(list.version()).isZero();
    }

    @Test
    void rejectsBlankOrTooLongNames() {
        assertThatThrownBy(() -> DistributionList.create(101, " ", 1, Channel.EMAIL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DistributionList.create(101, null, 1, Channel.EMAIL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DistributionList.create(101, "x".repeat(201), 1, Channel.EMAIL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void renamesDeactivatesAndReactivates() {
        DistributionList list = DistributionList.create(101, "Compliance", 1, Channel.EMAIL);

        list.rename(" QA ");
        list.deactivate();
        assertThat(list.name()).isEqualTo("QA");
        assertThat(list.isActive()).isFalse();

        list.activate();
        assertThat(list.isActive()).isTrue();
    }

    @Test
    void staleVersionIsRejected() {
        DistributionList list = DistributionList.create(101, "Compliance", 1, Channel.EMAIL);

        list.checkVersion(0);
        assertThatThrownBy(() -> list.checkVersion(3)).isInstanceOf(VersionConflictException.class);
    }
}
