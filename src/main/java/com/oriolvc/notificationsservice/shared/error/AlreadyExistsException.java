package com.oriolvc.notificationsservice.shared.error;

import java.util.List;

/** The change would duplicate an existing resource. */
public class AlreadyExistsException extends ProblemException {

    public AlreadyExistsException(String detail) {
        super(ProblemType.ALREADY_EXISTS, detail, List.of());
    }
}
