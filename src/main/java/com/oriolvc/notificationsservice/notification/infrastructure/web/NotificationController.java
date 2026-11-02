package com.oriolvc.notificationsservice.notification.infrastructure.web;

import com.oriolvc.notificationsservice.generated.api.NotificationsApi;
import com.oriolvc.notificationsservice.generated.model.CreateNotificationRequest;
import com.oriolvc.notificationsservice.generated.model.CreateNotificationResponse;
import com.oriolvc.notificationsservice.notification.application.CreateNotification;
import com.oriolvc.notificationsservice.notification.application.model.CreateNotificationResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Operations not implemented yet answer 501 through the generated default methods (T9). */
@RestController
class NotificationController implements NotificationsApi {

    private final CreateNotification createNotification;

    NotificationController(final CreateNotification createNotification) {
        this.createNotification = createNotification;
    }

    /** 202 if at least one notification was created, 200 if every customer had already been notified (D-9). */
    @Override
    public ResponseEntity<CreateNotificationResponse> createNotification(final CreateNotificationRequest request) {
        final CreateNotificationResult result = createNotification.execute(NotificationWebMapper.toCommand(request));
        return ResponseEntity.status(result.anyCreated() ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(NotificationWebMapper.toDto(result));
    }
}
