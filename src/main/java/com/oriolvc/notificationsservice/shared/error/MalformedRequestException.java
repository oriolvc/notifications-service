package com.oriolvc.notificationsservice.shared.error;

import java.util.List;

/** A value the request schema cannot express is invalid, e.g. an address that is not valid for the channel. */
public class MalformedRequestException extends ProblemException {

    public MalformedRequestException(final String detail, final List<Violation> violations) {
        super(ProblemType.MALFORMED_REQUEST, detail, violations);
    }
}
