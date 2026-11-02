package com.oriolvc.notificationsservice.distribution.infrastructure.persistence;

import com.oriolvc.notificationsservice.distribution.domain.DistributionList;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListRepository;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Owns the transactions: reads are read-only, {@link #save} writes in its own transaction. */
@Repository
@Transactional(readOnly = true)
class JpaDistributionListRepository implements DistributionListRepository {

    private final DistributionListDao dao;
    private final EntityManager entityManager;

    JpaDistributionListRepository(final DistributionListDao dao, final EntityManager entityManager) {
        this.dao = dao;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public DistributionList save(final DistributionList distributionList) {
        final boolean isNew = distributionList.id() == null;
        final DistributionListDbo dbo = isNew ? new DistributionListDbo() : currentRow(distributionList);
        DistributionListDboMapper.copy(distributionList, dbo);
        if (isNew) {
            dao.save(dbo);
        }
        if (DistributionListDboMapper.copyMembers(distributionList, dbo) && !isNew) {
            // Members belong to the aggregate: changing them bumps the list's version, so concurrent member changes
            // conflict instead of slipping past the active-member limit. The increment is immediate and checked.
            entityManager.lock(dbo, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        }
        dao.flush();
        return DistributionListDboMapper.toDomain(dbo);
    }

    @Override
    public Optional<DistributionList> findByIdAndCustomerId(final long id, final long customerId) {
        return dao.findByIdAndCustomerId(id, customerId).map(DistributionListDboMapper::toDomain);
    }

    @Override
    public List<DistributionList> findAllByCustomerIdOrderByIdAsc(final long customerId) {
        return dao.findAllByCustomerIdOrderByIdAsc(customerId).stream()
                .map(DistributionListDboMapper::toDomain)
                .toList();
    }

    @Override
    public List<DistributionList> findActiveByCustomerIdAndNotificationTypeId(
            final long customerId, final long notificationTypeId) {
        return dao.findAllByCustomerIdAndNotificationTypeIdAndActiveTrue(customerId, notificationTypeId).stream()
                .map(DistributionListDboMapper::toDomain)
                .toList();
    }

    /**
     * The row the list was read from. Its version must still be the one the domain object was read at; Hibernate then
     * checks it again in the {@code UPDATE}.
     */
    private DistributionListDbo currentRow(final DistributionList distributionList) {
        return dao.findById(distributionList.id())
                .filter(dbo -> dbo.getVersion() == distributionList.version())
                .orElseThrow(() ->
                        new ObjectOptimisticLockingFailureException(DistributionListDbo.class, distributionList.id()));
    }

    @Override
    public long countActive(final long notificationTypeId, final Channel channel) {
        return dao.countByNotificationTypeIdAndChannelAndActiveTrue(notificationTypeId, channel);
    }
}
