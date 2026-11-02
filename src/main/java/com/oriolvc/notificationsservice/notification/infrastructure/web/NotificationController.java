package com.oriolvc.notificationsservice.notification.infrastructure.web;

import com.oriolvc.notificationsservice.generated.api.NotificationsApi;
import com.oriolvc.notificationsservice.generated.model.CreateNotificationRequest;
import com.oriolvc.notificationsservice.generated.model.CreateNotificationResponse;
import com.oriolvc.notificationsservice.generated.model.NotificationDetail;
import com.oriolvc.notificationsservice.generated.model.NotificationSearchResult;
import com.oriolvc.notificationsservice.notification.application.CreateNotification;
import com.oriolvc.notificationsservice.notification.application.GetNotification;
import com.oriolvc.notificationsservice.notification.application.SearchNotifications;
import com.oriolvc.notificationsservice.notification.application.model.CreateNotificationResult;
import com.oriolvc.notificationsservice.notification.application.model.GetNotificationCommand;
import com.oriolvc.notificationsservice.notification.application.model.SearchNotificationsCommand;
import com.oriolvc.notificationsservice.shared.security.CurrentPrincipalProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Publishing events, and tracking them; tenant users only ever read their own tenant (from the JWT). */
@RestController
class NotificationController implements NotificationsApi {

    private final CurrentPrincipalProvider principals;
    private final CreateNotification createNotification;
    private final GetNotification getNotification;
    private final SearchNotifications searchNotifications;

    NotificationController(
            final CurrentPrincipalProvider principals,
            final CreateNotification createNotification,
            final GetNotification getNotification,
            final SearchNotifications searchNotifications) {
        this.principals = principals;
        this.createNotification = createNotification;
        this.getNotification = getNotification;
        this.searchNotifications = searchNotifications;
    }

    /** 202 if at least one notification was created, 200 if every customer had already been notified (D-9). */
    @Override
    public ResponseEntity<CreateNotificationResponse> createNotification(final CreateNotificationRequest request) {
        final CreateNotificationResult result = createNotification.execute(NotificationWebMapper.toCommand(request));
        return ResponseEntity.status(result.anyCreated() ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(NotificationWebMapper.toDto(result));
    }

    @Override
    public ResponseEntity<NotificationDetail> getNotification(final Long notificationId) {
        return ResponseEntity.ok(NotificationWebMapper.toDto(
                getNotification.execute(new GetNotificationCommand(notificationId, readScope()))));
    }

    @Override
    public ResponseEntity<NotificationSearchResult> searchNotifications(final String eventKey, final Long customerId) {
        return ResponseEntity.ok(NotificationWebMapper.toDto(
                searchNotifications.execute(new SearchNotificationsCommand(eventKey, customerId, readScope()))));
    }

    /** {@code null} for publishers, who read every tenant. */
    private Long readScope() {
        return principals.current().readScope().orElse(null);
    }
}
