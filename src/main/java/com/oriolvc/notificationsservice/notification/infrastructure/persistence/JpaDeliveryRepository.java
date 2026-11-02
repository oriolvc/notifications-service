package com.oriolvc.notificationsservice.notification.infrastructure.persistence;

import com.oriolvc.notificationsservice.notification.domain.Delivery;
import com.oriolvc.notificationsservice.notification.domain.DeliveryAttempt;
import com.oriolvc.notificationsservice.notification.domain.DeliveryRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
class JpaDeliveryRepository implements DeliveryRepository {

    private final DeliveryDao dao;

    JpaDeliveryRepository(final DeliveryDao dao) {
        this.dao = dao;
    }

    /**
     * The row lock is held while {@code attempt} sends, so a crash rolls back and releases it: the delivery is simply
     * due again, with no stuck-delivery detection needed (RFC 9).
     */
    @Override
    @Transactional
    public boolean attemptNextDue(final Instant now, final Consumer<Delivery> attempt) {
        final Optional<DeliveryDbo> locked = dao.lockNextDue(now);
        if (locked.isEmpty()) {
            return false;
        }
        final DeliveryDbo row = locked.get();
        final Delivery delivery = NotificationDboMapper.toDomain(row);
        attempt.accept(delivery);
        row.setDispatchState(
                delivery.status(),
                delivery.attemptCount(),
                delivery.nextAttemptAt(),
                delivery.sentAt(),
                delivery.providerMessageId());
        for (final DeliveryAttempt newAttempt : delivery.attempts()
                .subList(row.getAttempts().size(), delivery.attempts().size())) {
            row.getAttempts().add(NotificationDboMapper.toDbo(newAttempt, row));
        }
        dao.flush();
        return true;
    }
}
