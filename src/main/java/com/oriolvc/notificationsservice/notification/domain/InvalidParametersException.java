package com.oriolvc.notificationsservice.notification.domain;

import com.oriolvc.notificationsservice.shared.error.UnprocessableException;
import com.oriolvc.notificationsservice.shared.error.Violation;
import java.util.List;

/** The parameters do not satisfy the type's schema (AC-12). Never echoes their values. */
public class InvalidParametersException extends UnprocessableException {

    /** @param violations with pointers relative to the parameters; reported relative to the request body */
    public InvalidParametersException(final List<Violation> violations) {
        super(
                "The parameters do not satisfy the schema of the notification type",
                violations.stream()
                        .map(violation -> new Violation("/parameters" + violation.pointer(), violation.message()))
                        .toList());
    }
}
