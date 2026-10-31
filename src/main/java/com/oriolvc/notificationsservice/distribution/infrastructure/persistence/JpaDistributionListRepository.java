package com.oriolvc.notificationsservice.distribution.infrastructure.persistence;

import com.oriolvc.notificationsservice.distribution.domain.DistributionList;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListRepository;
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

    JpaDistributionListRepository(final DistributionListDao dao) {
        this.dao = dao;
    }

    @Override
    @Transactional
    public DistributionList save(final DistributionList distributionList) {
        final DistributionListDbo dbo =
                distributionList.id() == null ? new DistributionListDbo() : currentRow(distributionList);
        DistributionListDboMapper.copy(distributionList, dbo);
        return DistributionListDboMapper.toDomain(dao.saveAndFlush(dbo));
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

    /**
     * The row the list was read from (usually already in the persistence context). Its version must still be the one
     * the domain object was read at; Hibernate then checks it again in the {@code UPDATE}.
     */
    private DistributionListDbo currentRow(final DistributionList distributionList) {
        return dao.findById(distributionList.id())
                .filter(dbo -> dbo.getVersion() == distributionList.version())
                .orElseThrow(() ->
                        new ObjectOptimisticLockingFailureException(DistributionListDbo.class, distributionList.id()));
    }
}
