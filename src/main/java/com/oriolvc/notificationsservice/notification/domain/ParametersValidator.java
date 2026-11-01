package com.oriolvc.notificationsservice.notification.domain;

import com.oriolvc.notificationsservice.shared.error.Violation;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Validates parameters against their type's JSON Schema (RFC 7.2). */
public interface ParametersValidator {

    /**
     * @return one violation per problem, with a JSON pointer relative to the parameters ({@code /confidence}); empty
     *     when the parameters are valid
     */
    List<Violation> validate(ParametersSchema schema, JsonNode parameters);
}
