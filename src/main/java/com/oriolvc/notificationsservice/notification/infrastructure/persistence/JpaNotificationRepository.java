package com.oriolvc.notificationsservice.notification.infrastructure.persistence;

import com.oriolvc.notificationsservice.notification.domain.Notification;
import com.oriolvc.notificationsservice.notification.domain.NotificationRepository;
import com.oriolvc.notificationsservice.notification.domain.SavedNotification;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
class JpaNotificationRepository implements NotificationRepository {

    private final NotificationDao dao;
    private final DeliveryDao deliveryDao;
    private final EntityManager entityManager;
    private final DateTimeProvider dateTimeProvider;
    private final AuditorAware<String> auditorAware;

    JpaNotificationRepository(
            final NotificationDao dao,
            final DeliveryDao deliveryDao,
            final EntityManager entityManager,
            final DateTimeProvider dateTimeProvider,
            final AuditorAware<String> auditorAware) {
        this.dao = dao;
        this.deliveryDao = deliveryDao;
        this.entityManager = entityManager;
        this.dateTimeProvider = dateTimeProvider;
        this.auditorAware = auditorAware;
    }

    /**
     * One transaction for every customer (RFC 7.1). Inserted in customer order, so two concurrent requests for
     * overlapping customers wait on each other's rows in the same order and cannot deadlock.
     */
    @Override
    @Transactional
    public List<SavedNotification> saveAllIfAbsent(final List<Notification> notifications) {
        // The insert is native, so the audit columns that Spring Data fills for entities are set here, from the same
        // sources
        final Instant createdAt = Instant.from(dateTimeProvider.getNow().orElseThrow());
        final String createdBy = auditorAware.getCurrentAuditor().orElseThrow();
        final Map<Notification, Long> ids = new IdentityHashMap<>();
        final Set<Long> duplicates = new HashSet<>();
        notifications.stream()
                .sorted(Comparator.comparingLong(Notification::customerId))
                .forEach(notification -> {
                    final Optional<Long> inserted = dao.insertIfAbsent(
                            notification.notificationTypeId(),
                            notification.eventKey().value(),
                            notification.customerId(),
                            notification.parameters().toString(),
                            notification.outcome().name(),
                            createdAt,
                            createdBy);
                    if (inserted.isPresent()) {
                        final NotificationDbo row = entityManager.getReference(NotificationDbo.class, inserted.get());
                        notification
                                .deliveries()
                                .forEach(delivery -> deliveryDao.save(NotificationDboMapper.toDbo(delivery, row)));
                        ids.put(notification, inserted.get());
                    } else {
                        final long existing = dao.findId(
                                        notification.notificationTypeId(),
                                        notification.eventKey().value(),
                                        notification.customerId())
                                .orElseThrow();
                        ids.put(notification, existing);
                        duplicates.add(existing);
                    }
                });
        deliveryDao.flush();
        // Read back as stored, deliveries included
        entityManager.clear();
        final Map<Long, NotificationDbo> rows = dao.findAllByIdIn(ids.values()).stream()
                .collect(Collectors.toMap(NotificationDbo::getId, Function.identity()));
        return notifications.stream()
                .map(notification -> {
                    final long id = ids.get(notification);
                    return new SavedNotification(NotificationDboMapper.toDomain(rows.get(id)), duplicates.contains(id));
                })
                .toList();
    }
}
