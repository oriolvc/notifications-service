package com.oriolvc.notificationsservice.distribution.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oriolvc.notificationsservice.distribution.domain.ActiveDistributionListExistsException;
import com.oriolvc.notificationsservice.distribution.domain.DistributionList;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListNameTakenException;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListRepository;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** The race the use-case checks cannot see: the database rejects the second of two concurrent writes. */
class UniqueListGuardTest {

    private final DistributionList list = DistributionList.create(101, "Compliance", 1, Channel.EMAIL);

    @Test
    void activeListIndexViolationIsAnActiveListConflict() {
        assertThatThrownBy(() ->
                        guardFailingWith(UniqueListGuard.ACTIVE_LIST_INDEX).saveAndFlush(list, "TYPE"))
                .isInstanceOf(ActiveDistributionListExistsException.class);
    }

    @Test
    void nameConstraintViolationIsANameConflict() {
        assertThatThrownBy(
                        () -> guardFailingWith(UniqueListGuard.NAME_CONSTRAINT).saveAndFlush(list, "TYPE"))
                .isInstanceOf(DistributionListNameTakenException.class);
    }

    @Test
    void otherViolationsPropagate() {
        assertThatThrownBy(() -> guardFailingWith("fk_distribution_lists_type").saveAndFlush(list, "TYPE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static UniqueListGuard guardFailingWith(String constraint) {
        return new UniqueListGuard(new FailingRepository(
                new DataIntegrityViolationException("violates unique constraint \"" + constraint + "\"")));
    }

    private record FailingRepository(DataIntegrityViolationException failure) implements DistributionListRepository {

        @Override
        public DistributionList saveAndFlush(DistributionList list) {
            throw failure;
        }

        @Override
        public Optional<DistributionList> findByIdAndCustomerId(Long id, long customerId) {
            return Optional.empty();
        }

        @Override
        public List<DistributionList> findAllByCustomerIdOrderByIdAsc(long customerId) {
            return List.of();
        }

        @Override
        public boolean existsByCustomerIdAndNotificationTypeIdAndChannelAndActiveTrue(
                long customerId, long notificationTypeId, Channel channel) {
            return false;
        }

        @Override
        public boolean existsByCustomerIdAndName(long customerId, String name) {
            return false;
        }
    }
}
