package com.oriolvc.notificationsservice.notification.infrastructure.web;

import com.oriolvc.notificationsservice.generated.model.CreateNotificationRequest;
import com.oriolvc.notificationsservice.notification.domain.exceptions.UnknownNotificationTypeException;
import com.oriolvc.notificationsservice.shared.error.ProblemException;
import com.oriolvc.notificationsservice.shared.error.UnreadableBodyTranslator;
import java.util.List;
import java.util.Optional;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;

/**
 * The generated request binds {@code type} to an enum of the known types, so an unknown type fails while binding. It
 * is reported like an inactive type, a 422 (RFC 6.3, AC-11), not as a malformed body.
 */
@Component
class UnknownTypeTranslator implements UnreadableBodyTranslator {

    @Override
    public Optional<ProblemException> translate(final HttpMessageNotReadableException exception) {
        if (exception.getCause() instanceof DatabindException databind && isRequestType(databind.getPath())) {
            return Optional.of(new UnknownNotificationTypeException());
        }
        return Optional.empty();
    }

    private static boolean isRequestType(final List<JacksonException.Reference> path) {
        return path.size() == 1
                && "type".equals(path.getFirst().getPropertyName())
                && (path.getFirst().from() instanceof CreateNotificationRequest
                        || path.getFirst().from() == CreateNotificationRequest.class);
    }
}
