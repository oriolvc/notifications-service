package com.oriolvc.notificationsservice.distribution.infrastructure.persistence;

import com.oriolvc.notificationsservice.distribution.domain.DistributionList;
import com.oriolvc.notificationsservice.distribution.domain.DistributionListRepository;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataDistributionListRepository
        extends JpaRepository<DistributionList, Long>, DistributionListRepository {}
