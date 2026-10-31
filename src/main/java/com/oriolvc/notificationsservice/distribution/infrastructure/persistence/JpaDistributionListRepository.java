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

    JpaDistributionListRepository(DistributionListDao dao) {
        this.dao = dao;
    }

    @Override
    @Transactional
    public DistributionList save(DistributionList list) {
        DistributionListDbo dbo = list.id() == null ? new DistributionListDbo() : currentRow(list);
        DistributionListDboMapper.copy(list, dbo);
        return DistributionListDboMapper.toDomain(dao.saveAndFlush(dbo));
    }

    @Override
    public Optional<DistributionList> findByIdAndCustomerId(long id, long customerId) {
        return dao.findByIdAndCustomerId(id, customerId).map(DistributionListDboMapper::toDomain);
    }

    @Override
    public List<DistributionList> findAllByCustomerIdOrderByIdAsc(long customerId) {
        return dao.findAllByCustomerIdOrderByIdAsc(customerId).stream()
                .map(DistributionListDboMapper::toDomain)
                .toList();
    }

    /**
     * The row the list was read from (usually already in the persistence context). Its version must still be the one
     * the domain object was read at; Hibernate then checks it again in the {@code UPDATE}.
     */
    private DistributionListDbo currentRow(DistributionList list) {
        return dao.findById(list.id())
                .filter(dbo -> dbo.getVersion() == list.version())
                .orElseThrow(() -> new ObjectOptimisticLockingFailureException(DistributionListDbo.class, list.id()));
    }
}
