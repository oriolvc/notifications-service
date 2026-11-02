package com.oriolvc.notificationsservice.notification.domain;

import com.oriolvc.notificationsservice.shared.error.UnprocessableException;
import com.oriolvc.notificationsservice.shared.error.Violation;
import java.util.List;

/** The type does not exist or is inactive (AC-11). */
public class UnknownNotificationTypeException extends UnprocessableException {

    public static final String DETAIL = "Unknown or inactive notification type";
    public static final Violation VIOLATION = new Violation("/type", "unknown or inactive notification type");

    public UnknownNotificationTypeException() {
        super(DETAIL, List.of(VIOLATION));
    }
}
