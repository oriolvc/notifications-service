package com.oriolvc.notificationsservice.shared.error;

import java.util.List;

/** Well-formed but semantically invalid request: unknown type, schema violations, business limits. */
public class UnprocessableException extends ProblemException {

    public UnprocessableException(final String detail) {
        this(detail, List.of());
    }

    public UnprocessableException(final String detail, final List<Violation> violations) {
        super(ProblemType.UNPROCESSABLE_CONTENT, detail, violations);
    }
}
