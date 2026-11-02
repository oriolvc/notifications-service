package com.oriolvc.notificationsservice.notification.infrastructure.scheduling;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.oriolvc.notificationsservice.notification.application.DispatchDueDeliveries;
import com.oriolvc.notificationsservice.notification.application.model.NotificationsAccepted;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The triggers only delegate; disabled in the integration tests, which dispatch synchronously. */
class DispatchTriggersTest {

    private final DispatchDueDeliveries dispatch = mock(DispatchDueDeliveries.class);
    private final DispatchTriggers triggers = new DispatchTriggers(dispatch);

    @Test
    void pollingAndAcceptedNotificationsBothDispatch() {
        triggers.poll();
        triggers.onNotificationsAccepted(new NotificationsAccepted(List.of(1L)));

        verify(dispatch, times(2)).execute();
    }
}
