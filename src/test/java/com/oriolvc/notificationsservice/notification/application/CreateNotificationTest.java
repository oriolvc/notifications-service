package com.oriolvc.notificationsservice.notification.application;

import static com.oriolvc.notificationsservice.notification.domain.RegulatoryDocumentMatched.sampleParameters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;

import com.oriolvc.notificationsservice.notification.application.model.CreateNotificationCommand;
import com.oriolvc.notificationsservice.notification.application.model.CreateNotificationResult;
import com.oriolvc.notificationsservice.notification.application.model.CustomerOutcome;
import com.oriolvc.notificationsservice.notification.application.model.CustomerResult;
import com.oriolvc.notificationsservice.notification.application.model.NotificationsAccepted;
import com.oriolvc.notificationsservice.notification.domain.EventKey;
import com.oriolvc.notificationsservice.notification.domain.Notification;
import com.oriolvc.notificationsservice.notification.domain.NotificationRepository;
import com.oriolvc.notificationsservice.notification.domain.NotificationSummary;
import com.oriolvc.notificationsservice.notification.domain.NotificationType;
import com.oriolvc.notificationsservice.notification.domain.NotificationTypeRepository;
import com.oriolvc.notificationsservice.notification.domain.Recipient;
import com.oriolvc.notificationsservice.notification.domain.RegulatoryDocumentMatched;
import com.oriolvc.notificationsservice.notification.domain.SavedNotification;
import com.oriolvc.notificationsservice.notification.domain.UnknownNotificationTypeException;
import com.oriolvc.notificationsservice.notification.domain.parameters.InvalidParametersException;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import com.oriolvc.notificationsservice.shared.error.Violation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

/** The use case's own logic with fakes; persistence, concurrency and HTTP are in CreateNotificationIntegrationTest. */
class CreateNotificationTest {

    private static final NotificationType TYPE = RegulatoryDocumentMatched.TYPE;

    private final FakeTypes types = new FakeTypes();
    private final List<Violation> violations = new ArrayList<>();
    private final FakeNotifications notifications = new FakeNotifications();
    private final List<Object> events = new ArrayList<>();
    private final RecordingNotificationMetrics metrics = new RecordingNotificationMetrics();
    private final CreateNotification createNotification = new CreateNotification(
            types,
            (schema, parameters) -> List.copyOf(violations),
            (customerId, typeId) -> customerId == 101
                    ? List.of(new Recipient(1001, 1101, Channel.EMAIL, "alice@customer101.example"))
                    : List.of(),
            notifications,
            events::add,
            metrics,
            Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void reportsEachCustomerOnceInTheOrderGiven() {
        final CreateNotificationResult result = createNotification.execute(command(List.of(103L, 101L, 103L)));

        assertThat(result.results())
                .extracting(CustomerResult::customerId, CustomerResult::outcome, CustomerResult::deliveries)
                .containsExactly(
                        tuple(103L, CustomerOutcome.NO_RECIPIENTS, 0), tuple(101L, CustomerOutcome.ACCEPTED, 1));
        assertThat(result.anyCreated()).isTrue();
        assertThat(events).containsExactly(new NotificationsAccepted(List.of(2L)));
        assertThat(metrics.recorded)
                .containsExactly(
                        "created REGULATORY_DOCUMENT_MATCHED NO_RECIPIENTS to 0",
                        "created REGULATORY_DOCUMENT_MATCHED ACCEPTED to 1",
                        "reached REGULATORY_DOCUMENT_MATCHED 0.5");
    }

    @Test
    void duplicatesAreReportedWithTheStoredNotificationAndPublishNothing() {
        final CreateNotificationResult first = createNotification.execute(command(List.of(101L)));
        events.clear();
        metrics.recorded.clear();

        final CreateNotificationResult second = createNotification.execute(command(List.of(101L)));

        assertThat(second.eventKey()).isEqualTo(first.eventKey());
        assertThat(second.results())
                .containsExactly(new CustomerResult(
                        101, first.results().getFirst().notificationId(), CustomerOutcome.DUPLICATE, 1));
        assertThat(second.anyCreated()).isFalse();
        assertThat(events).isEmpty();
        assertThat(metrics.recorded).as("a replayed event is not counted again").isEmpty();
    }

    @Test
    void validatesAndStoresTheNormalizedParameters() {
        final ObjectNode parameters = sampleParameters();
        parameters.put("siteName", "  Qualifyze   Spain ");

        createNotification.execute(new CreateNotificationCommand(TYPE.code(), List.of(101L), parameters));

        assertThat(notifications.stored.values())
                .singleElement()
                .extracting(notification ->
                        notification.parameters().get("siteName").stringValue())
                .isEqualTo("Qualifyze Spain");
    }

    @Test
    void violationsPointIntoTheRequestBody() {
        violations.add(new Violation("/confidence", "must be at most 100"));

        assertThatExceptionOfType(InvalidParametersException.class)
                .isThrownBy(() -> createNotification.execute(command(List.of(101L))))
                .extracting(InvalidParametersException::violations)
                .isEqualTo(List.of(new Violation("/parameters/confidence", "must be at most 100")));
        assertThat(notifications.stored).isEmpty();
    }

    @Test
    void anInactiveTypeIsUnknown() {
        types.type = new NotificationType(
                TYPE.id(), TYPE.code(), TYPE.name(), false, TYPE.parametersSchema(), TYPE.templates());

        assertThatExceptionOfType(UnknownNotificationTypeException.class)
                .isThrownBy(() -> createNotification.execute(command(List.of(101L))));
    }

    private static CreateNotificationCommand command(final List<Long> customerIds) {
        return new CreateNotificationCommand(TYPE.code(), customerIds, sampleParameters());
    }

    private static final class FakeTypes implements NotificationTypeRepository {

        private NotificationType type = TYPE;

        @Override
        public Optional<NotificationType> findByCode(final String code) {
            return Optional.of(type).filter(candidate -> candidate.code().equals(code));
        }

        @Override
        public Optional<NotificationType> findById(final long id) {
            return Optional.of(type).filter(candidate -> candidate.id() == id);
        }

        @Override
        public List<NotificationType> findAll() {
            return List.of(type);
        }
    }

    /** Keyed like the unique constraint: (type, event key, customer). */
    private static final class FakeNotifications implements NotificationRepository {

        private final Map<String, Notification> stored = new HashMap<>();

        @Override
        public List<SavedNotification> saveAllIfAbsent(final List<Notification> notifications) {
            return notifications.stream()
                    .map(notification -> {
                        final String key = notification.notificationTypeId() + "/" + notification.eventKey() + "/"
                                + notification.customerId();
                        final Notification existing = stored.get(key);
                        if (existing != null) {
                            return new SavedNotification(existing, true);
                        }
                        final Notification saved = Notification.restore(
                                stored.size() + 1L,
                                notification.notificationTypeId(),
                                notification.eventKey(),
                                notification.customerId(),
                                notification.parameters(),
                                notification.outcome(),
                                Instant.EPOCH,
                                "test",
                                notification.deliveries());
                        stored.put(key, saved);
                        return new SavedNotification(saved, false);
                    })
                    .toList();
        }

        @Override
        public Optional<Notification> findById(final long id) {
            return stored.values().stream()
                    .filter(notification -> notification.id() == id)
                    .findFirst();
        }

        @Override
        public List<NotificationSummary> search(final Long customerId, final EventKey eventKey, final int limit) {
            throw new UnsupportedOperationException("Not used by CreateNotification");
        }
    }
}
