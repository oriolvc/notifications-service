package com.oriolvc.notificationsservice.distribution.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oriolvc.notificationsservice.shared.domain.Channel;
import com.oriolvc.notificationsservice.shared.error.VersionConflictException;
import org.junit.jupiter.api.Test;

class DistributionListTest {

    @Test
    void createsAnActiveListWithATrimmedName() {
        DistributionList distributionList = DistributionList.create(101, "  Compliance  ", 1, Channel.EMAIL);

        assertThat(distributionList.isActive()).isTrue();
        assertThat(distributionList.name()).isEqualTo("Compliance");
        assertThat(distributionList.customerId()).isEqualTo(101);
        assertThat(distributionList.notificationTypeId()).isEqualTo(1);
        assertThat(distributionList.channel()).isEqualTo(Channel.EMAIL);
        assertThat(distributionList.version()).isZero();
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
        DistributionList distributionList = DistributionList.create(101, "Compliance", 1, Channel.EMAIL);

        distributionList.rename(" QA ");
        distributionList.deactivate();
        assertThat(distributionList.name()).isEqualTo("QA");
        assertThat(distributionList.isActive()).isFalse();

        distributionList.activate();
        assertThat(distributionList.isActive()).isTrue();
    }

    @Test
    void staleVersionIsRejected() {
        DistributionList distributionList = DistributionList.create(101, "Compliance", 1, Channel.EMAIL);

        distributionList.checkVersion(0);
        assertThatThrownBy(() -> distributionList.checkVersion(3)).isInstanceOf(VersionConflictException.class);
    }
}
