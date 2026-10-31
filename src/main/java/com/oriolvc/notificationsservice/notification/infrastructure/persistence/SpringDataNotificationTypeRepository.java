package com.oriolvc.notificationsservice.notification.infrastructure.persistence;

import com.oriolvc.notificationsservice.notification.domain.NotificationType;
import com.oriolvc.notificationsservice.notification.domain.NotificationTypeRepository;
import org.springframework.data.repository.Repository;

interface SpringDataNotificationTypeRepository extends Repository<NotificationType, Long>, NotificationTypeRepository {}
