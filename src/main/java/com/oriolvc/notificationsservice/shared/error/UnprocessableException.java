package com.oriolvc.notificationsservice.shared.error;

import java.util.List;

/** Well-formed but semantically invalid request: unknown type, schema violations, business limits. */
public class UnprocessableException extends ProblemException {

    public UnprocessableException(String detail) {
        this(detail, List.of());
    }

    public UnprocessableException(String detail, List<Violation> violations) {
        super(ProblemType.UNPROCESSABLE_CONTENT, detail, violations);
    }
}
