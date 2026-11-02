package com.oriolvc.notificationsservice.shared.error;

import java.util.Optional;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * Lets a context report a body that could not be bound as its own problem instead of the generic 400, e.g. an unknown
 * notification type, which the generated DTO rejects while binding, is the same 422 as an inactive one.
 */
public interface UnreadableBodyTranslator {

    /** @return the problem to report; empty to leave the exception to the generic 400 */
    Optional<ProblemException> translate(HttpMessageNotReadableException exception);
}
