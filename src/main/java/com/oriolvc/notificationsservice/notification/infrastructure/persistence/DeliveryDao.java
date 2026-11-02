package com.oriolvc.notificationsservice.notification.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface DeliveryDao extends JpaRepository<DeliveryDbo, Long> {}
