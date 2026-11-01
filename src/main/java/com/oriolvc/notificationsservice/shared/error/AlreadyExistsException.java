package com.oriolvc.notificationsservice.shared.error;

import java.util.List;

/**
 * The change would duplicate an existing resource, detected by the domain. Duplicates that only the database can see
 * (unique constraints) become the same 409 in {@link ProblemDetailsExceptionHandler}.
 */
public class AlreadyExistsException extends ProblemException {

    public AlreadyExistsException(final String detail) {
        super(ProblemType.ALREADY_EXISTS, detail, List.of());
    }
}
