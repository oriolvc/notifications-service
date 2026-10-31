package com.oriolvc.notificationsservice.shared.error;

import java.util.List;

/** The client sent a stale {@code version}. */
public class VersionConflictException extends ProblemException {

    public VersionConflictException(String detail) {
        super(ProblemType.VERSION_CONFLICT, detail, List.of());
    }
}
