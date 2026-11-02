package com.oriolvc.notificationsservice.notification.application;

import com.oriolvc.notificationsservice.notification.application.model.CreateNotificationCommand;
import com.oriolvc.notificationsservice.notification.application.model.CreateNotificationResult;
import com.oriolvc.notificationsservice.notification.application.model.CustomerOutcome;
import com.oriolvc.notificationsservice.notification.application.model.CustomerResult;
import com.oriolvc.notificationsservice.notification.application.model.NotificationsAccepted;
import com.oriolvc.notificationsservice.notification.application.ports.NotificationMetrics;
import com.oriolvc.notificationsservice.notification.application.ports.RecipientResolver;
import com.oriolvc.notificationsservice.notification.domain.EventKey;
import com.oriolvc.notificationsservice.notification.domain.Notification;
import com.oriolvc.notificationsservice.notification.domain.NotificationOutcome;
import com.oriolvc.notificationsservice.notification.domain.NotificationType;
import com.oriolvc.notificationsservice.notification.domain.SavedNotification;
import com.oriolvc.notificationsservice.notification.domain.exceptions.UnknownNotificationTypeException;
import com.oriolvc.notificationsservice.notification.domain.parameters.InvalidParametersException;
import com.oriolvc.notificationsservice.notification.domain.parameters.ParameterNormalizer;
import com.oriolvc.notificationsservice.notification.domain.parameters.ParametersValidator;
import com.oriolvc.notificationsservice.notification.domain.ports.NotificationRepository;
import com.oriolvc.notificationsservice.notification.domain.ports.NotificationTypeRepository;
import com.oriolvc.notificationsservice.shared.error.Violation;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * Accepts an event for a set of customers (RFC 7.1): one notification per customer with a {@code PENDING} delivery per
 * recipient, unless the customer was already notified of the same event.
 */
@Service
public class CreateNotification {

    private static final Logger log = LoggerFactory.getLogger(CreateNotification.class);

    private final NotificationTypeRepository notificationTypeRepository;
    private final ParametersValidator parametersValidator;
    private final RecipientResolver recipientResolver;
    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher events;
    private final NotificationMetrics metrics;
    private final Clock clock;

    CreateNotification(
            final NotificationTypeRepository notificationTypeRepository,
            final ParametersValidator parametersValidator,
            final RecipientResolver recipientResolver,
            final NotificationRepository notificationRepository,
            final ApplicationEventPublisher events,
            final NotificationMetrics metrics,
            final Clock clock) {
        this.notificationTypeRepository = notificationTypeRepository;
        this.parametersValidator = parametersValidator;
        this.recipientResolver = recipientResolver;
        this.notificationRepository = notificationRepository;
        this.events = events;
        this.metrics = metrics;
        this.clock = clock;
    }

    public CreateNotificationResult execute(final CreateNotificationCommand command) {
        final NotificationType type = notificationTypeRepository
                .findByCode(command.type())
                .filter(NotificationType::active)
                .orElseThrow(UnknownNotificationTypeException::new);
        // Normalized first, so that what is validated is what gets hashed, stored and rendered
        final JsonNode parameters = ParameterNormalizer.normalize(command.parameters());
        final List<Violation> violations = parametersValidator.validate(type.parametersSchema(), parameters);
        if (!violations.isEmpty()) {
            throw new InvalidParametersException(violations);
        }
        final EventKey eventKey = EventKey.of(type.code(), parameters);
        final Instant now = clock.instant();

        final List<Notification> notifications = command.customerIds().stream()
                .distinct()
                .map(customerId -> Notification.create(
                        type, eventKey, customerId, parameters, recipientResolver.resolve(customerId, type.id()), now))
                .toList();
        // Deduplication is the unique constraint's job: duplicates come back flagged, in the same order
        final List<SavedNotification> saved = notificationRepository.saveAllIfAbsent(notifications);

        final List<Notification> created = saved.stream()
                .filter(result -> !result.duplicate())
                .map(SavedNotification::notification)
                .toList();
        final List<Long> accepted = created.stream()
                .filter(notification -> notification.outcome() == NotificationOutcome.ACCEPTED)
                .map(Notification::id)
                .toList();
        recordMetrics(type.code(), created, accepted.size());
        if (!accepted.isEmpty()) {
            // The repository has committed: the dispatcher can pick the deliveries up
            events.publishEvent(new NotificationsAccepted(accepted));
        }
        log.info(
                "Event {} of type {}: {} customers, {} new notifications with deliveries",
                eventKey,
                type.code(),
                saved.size(),
                accepted.size());
        return new CreateNotificationResult(
                eventKey.value(),
                saved.stream().map(CreateNotification::toResult).toList());
    }

    /** A replayed event is not counted again. */
    private void recordMetrics(final String type, final List<Notification> created, final int accepted) {
        if (created.isEmpty()) {
            return;
        }
        created.forEach(notification -> metrics.notificationCreated(type, notification));
        metrics.customersReached(type, (double) accepted / created.size());
    }

    private static CustomerResult toResult(final SavedNotification saved) {
        final Notification notification = saved.notification();
        final CustomerOutcome outcome = saved.duplicate()
                ? CustomerOutcome.DUPLICATE
                : CustomerOutcome.valueOf(notification.outcome().name());
        return new CustomerResult(
                notification.customerId(),
                notification.id(),
                outcome,
                notification.deliveries().size());
    }
}
