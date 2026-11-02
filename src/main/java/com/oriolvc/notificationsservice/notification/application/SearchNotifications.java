package com.oriolvc.notificationsservice.notification.application;

import com.oriolvc.notificationsservice.notification.application.model.NotificationSummaryView;
import com.oriolvc.notificationsservice.notification.application.model.SearchNotificationsCommand;
import com.oriolvc.notificationsservice.notification.domain.EventKey;
import com.oriolvc.notificationsservice.notification.domain.NotificationType;
import com.oriolvc.notificationsservice.notification.domain.ports.NotificationRepository;
import com.oriolvc.notificationsservice.notification.domain.ports.NotificationTypeRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Search by event reference and customer, newest first, at most {@value #MAX_RESULTS}. Tenant users only ever see their
 * own tenant: filtering by another customer returns nothing.
 */
@Service
public class SearchNotifications {

    public static final int MAX_RESULTS = 100;

    private final NotificationRepository notificationRepository;
    private final NotificationTypeRepository notificationTypeRepository;

    SearchNotifications(
            final NotificationRepository notificationRepository,
            final NotificationTypeRepository notificationTypeRepository) {
        this.notificationRepository = notificationRepository;
        this.notificationTypeRepository = notificationTypeRepository;
    }

    public List<NotificationSummaryView> execute(final SearchNotificationsCommand command) {
        final Long tenantId = command.tenantId();
        if (tenantId != null && command.customerId() != null && !tenantId.equals(command.customerId())) {
            return List.of();
        }
        final Long customerId = tenantId != null ? tenantId : command.customerId();
        final EventKey eventKey = command.eventKey() == null ? null : new EventKey(command.eventKey());
        final Map<Long, String> typeCodes = notificationTypeRepository.findAll().stream()
                .collect(Collectors.toMap(NotificationType::id, NotificationType::code));
        return notificationRepository.search(customerId, eventKey, MAX_RESULTS).stream()
                .map(summary -> new NotificationSummaryView(
                        summary.id(),
                        typeCodes.get(summary.notificationTypeId()),
                        summary.eventKey().value(),
                        summary.customerId(),
                        summary.outcome(),
                        summary.createdAt(),
                        summary.createdBy()))
                .toList();
    }
}
