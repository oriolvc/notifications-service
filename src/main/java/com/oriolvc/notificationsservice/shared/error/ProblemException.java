package com.oriolvc.notificationsservice.shared.error;

import java.util.List;

/**
 * Base class for exceptions that the application layer throws to report a client error. Each context extends the
 * subclasses ({@link NotFoundException}, ...) with its own exceptions; the web layer maps them all to Problem Details.
 */
public abstract class ProblemException extends RuntimeException {

    private final ProblemType type;
    private final List<Violation> violations;

    protected ProblemException(final ProblemType type, final String detail, final List<Violation> violations) {
        super(detail);
        this.type = type;
        this.violations = List.copyOf(violations);
    }

    public ProblemType type() {
        return type;
    }

    public List<Violation> violations() {
        return violations;
    }
}
