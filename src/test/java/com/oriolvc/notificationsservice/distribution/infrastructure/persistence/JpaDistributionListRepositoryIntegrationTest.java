package com.oriolvc.notificationsservice.distribution.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oriolvc.notificationsservice.IntegrationTest;
import com.oriolvc.notificationsservice.distribution.domain.DistributionList;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListRepository;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/** Domain ↔ DBO mapping and the version check of the persistence adapter. */
class JpaDistributionListRepositoryIntegrationTest extends IntegrationTest {

    @Autowired
    private DistributionListRepository lists;

    private long typeId() {
        return jdbc.queryForObject(
                "SELECT id FROM notification_types WHERE code = 'REGULATORY_DOCUMENT_MATCHED'", Long.class);
    }

    @Test
    void savesANewListAndReadsItBack() {
        DistributionList saved = lists.save(DistributionList.create(101, "Compliance", typeId(), Channel.EMAIL));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.version()).isZero();
        assertThat(saved.audit().createdBy()).isEqualTo("system");
        assertThat(saved.audit().createdAt())
                .isNotNull()
                .isEqualTo(saved.audit().updatedAt());

        assertThat(lists.findByIdAndCustomerId(saved.id(), 101))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(saved);
        assertThat(lists.findByIdAndCustomerId(saved.id(), 102)).isEmpty();
    }

    @Test
    void savingAChangeBumpsTheVersion() {
        DistributionList list = lists.save(DistributionList.create(101, "Compliance", typeId(), Channel.EMAIL));

        list.rename("QA");
        list.deactivate();
        DistributionList updated = lists.save(list);

        assertThat(updated.version()).isOne();
        assertThat(updated.name()).isEqualTo("QA");
        assertThat(updated.isActive()).isFalse();
        assertThat(lists.findAllByCustomerIdOrderByIdAsc(101))
                .singleElement()
                .usingRecursiveComparison()
                .isEqualTo(updated);
    }

    @Test
    void savingACopyReadBeforeAnotherChangeIsRejected() {
        long id = lists.save(DistributionList.create(101, "Compliance", typeId(), Channel.EMAIL))
                .id();
        DistributionList first = lists.findByIdAndCustomerId(id, 101).orElseThrow();
        DistributionList second = lists.findByIdAndCustomerId(id, 101).orElseThrow();

        first.rename("First");
        lists.save(first);
        second.rename("Second");

        assertThatThrownBy(() -> lists.save(second)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(lists.findByIdAndCustomerId(id, 101).orElseThrow().name()).isEqualTo("First");
    }
}
