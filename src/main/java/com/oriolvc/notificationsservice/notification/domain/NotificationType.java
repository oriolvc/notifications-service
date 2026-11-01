package com.oriolvc.notificationsservice.notification.domain;

import com.oriolvc.notificationsservice.shared.domain.Channel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A kind of notification: the schema its parameters must follow and one template per channel. Read-only: types are
 * created and changed by Liquibase migrations only (D-6).
 */
public record NotificationType(
        long id,
        String code,
        String name,
        boolean active,
        ParametersSchema parametersSchema,
        Map<Channel, Template> templates) {

    public NotificationType {
        templates = Map.copyOf(templates);
    }

    public Optional<Template> template(final Channel channel) {
        return Optional.ofNullable(templates.get(channel));
    }

    /** RFC 8: every placeholder must be a property of the schema. Empty when the type is consistent. */
    public List<String> templateProblems() {
        final List<String> problems = new ArrayList<>();
        templates
                .values()
                .forEach(template -> template.placeholders().stream()
                        .filter(placeholder -> !parametersSchema.hasProperty(placeholder))
                        .sorted()
                        .forEach(placeholder -> problems.add("The " + template.channel() + " template of " + code
                                + " uses {{" + placeholder + "}}, which is not a property of its parameters schema")));
        return problems;
    }
}
