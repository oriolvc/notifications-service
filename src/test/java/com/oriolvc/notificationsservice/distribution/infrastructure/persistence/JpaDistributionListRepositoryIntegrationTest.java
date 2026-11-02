package com.oriolvc.notificationsservice.distribution.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oriolvc.notificationsservice.IntegrationTest;
import com.oriolvc.notificationsservice.distribution.domain.DistributionList;
import com.oriolvc.notificationsservice.distribution.domain.Member;
import com.oriolvc.notificationsservice.distribution.domain.ports.AddressValidator;
import com.oriolvc.notificationsservice.distribution.domain.ports.DistributionListRepository;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/** Domain ↔ DBO mapping and the version check of the persistence adapter. */
class JpaDistributionListRepositoryIntegrationTest extends IntegrationTest {

    /** Accepts any address; the email rules are tested in {@code EmailAddressValidatorTest}. */
    private static final AddressValidator EMAIL = new AddressValidator() {
        @Override
        public Channel channel() {
            return Channel.EMAIL;
        }

        @Override
        public void validate(final String address) {}
    };

    @Autowired
    private DistributionListRepository distributionListRepository;

    private long typeId() {
        return jdbc.queryForObject(
                "SELECT id FROM notification_types WHERE code = 'REGULATORY_DOCUMENT_MATCHED'", Long.class);
    }

    @Test
    void savesANewListAndReadsItBack() {
        final DistributionList saved =
                distributionListRepository.save(DistributionList.create(101, "Compliance", typeId(), Channel.EMAIL));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.version()).isZero();
        assertThat(saved.audit().createdBy()).isEqualTo("system");
        assertThat(saved.audit().createdAt())
                .isNotNull()
                .isEqualTo(saved.audit().updatedAt());

        assertThat(distributionListRepository.findByIdAndCustomerId(saved.id(), 101))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(saved);
        assertThat(distributionListRepository.findByIdAndCustomerId(saved.id(), 102))
                .isEmpty();
    }

    @Test
    void savingAChangeBumpsTheVersion() {
        final DistributionList distributionList =
                distributionListRepository.save(DistributionList.create(101, "Compliance", typeId(), Channel.EMAIL));

        distributionList.rename("QA");
        distributionList.deactivate();
        final DistributionList updated = distributionListRepository.save(distributionList);

        assertThat(updated.version()).isOne();
        assertThat(updated.name()).isEqualTo("QA");
        assertThat(updated.isActive()).isFalse();
        assertThat(distributionListRepository.findAllByCustomerIdOrderByIdAsc(101))
                .singleElement()
                .usingRecursiveComparison()
                .isEqualTo(updated);
    }

    @Test
    void savingACopyReadBeforeAnotherChangeIsRejected() {
        final long id = distributionListRepository
                .save(DistributionList.create(101, "Compliance", typeId(), Channel.EMAIL))
                .id();
        final DistributionList first =
                distributionListRepository.findByIdAndCustomerId(id, 101).orElseThrow();
        final DistributionList second =
                distributionListRepository.findByIdAndCustomerId(id, 101).orElseThrow();

        first.rename("First");
        distributionListRepository.save(first);
        second.rename("Second");

        assertThatThrownBy(() -> distributionListRepository.save(second))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(distributionListRepository
                        .findByIdAndCustomerId(id, 101)
                        .orElseThrow()
                        .name())
                .isEqualTo("First");
    }

    @Test
    void savesNewMembersAndBumpsTheListVersion() {
        final DistributionList distributionList =
                distributionListRepository.save(DistributionList.create(101, "Compliance", typeId(), Channel.EMAIL));

        distributionList.addMember("ana@example.com", EMAIL);
        final DistributionList saved = distributionListRepository.save(distributionList);

        assertThat(saved.version()).isOne();
        assertThat(saved.members()).singleElement().satisfies(member -> {
            assertThat(member.id()).isNotNull();
            assertThat(member.audit().createdBy()).isEqualTo("system");
        });

        saved.removeMember(saved.members().getFirst().id());
        final DistributionList removed = distributionListRepository.save(saved);

        assertThat(removed.version()).isEqualTo(2);
        assertThat(removed.activeMembers()).isEmpty();
    }

    @Test
    void concurrentMemberChangesConflict() {
        final long id = distributionListRepository
                .save(DistributionList.create(101, "Compliance", typeId(), Channel.EMAIL))
                .id();
        final DistributionList first =
                distributionListRepository.findByIdAndCustomerId(id, 101).orElseThrow();
        final DistributionList second =
                distributionListRepository.findByIdAndCustomerId(id, 101).orElseThrow();

        first.addMember("ana@example.com", EMAIL);
        distributionListRepository.save(first);
        second.addMember("bo@example.com", EMAIL);

        assertThatThrownBy(() -> distributionListRepository.save(second))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(distributionListRepository
                        .findByIdAndCustomerId(id, 101)
                        .orElseThrow()
                        .members())
                .extracting(Member::address)
                .containsExactly("ana@example.com");
    }

    @Test
    void findsTheCustomersActiveListsOfAType() {
        final DistributionList active =
                distributionListRepository.save(DistributionList.create(101, "Active", typeId(), Channel.EMAIL));
        final DistributionList inactive = DistributionList.create(101, "Inactive", typeId(), Channel.EMAIL);
        inactive.deactivate();
        distributionListRepository.save(inactive);

        assertThat(distributionListRepository.findActiveByCustomerIdAndNotificationTypeId(101, typeId()))
                .extracting(DistributionList::id)
                .containsExactly(active.id());
    }
}
