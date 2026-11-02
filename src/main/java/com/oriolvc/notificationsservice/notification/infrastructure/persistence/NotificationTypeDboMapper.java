package com.oriolvc.notificationsservice.notification.infrastructure.persistence;

import com.oriolvc.notificationsservice.notification.domain.NotificationType;
import com.oriolvc.notificationsservice.notification.domain.Template;
import com.oriolvc.notificationsservice.notification.domain.parameters.ParametersSchema;
import java.util.function.Function;
import java.util.stream.Collectors;
import tools.jackson.databind.json.JsonMapper;

final class NotificationTypeDboMapper {

    private NotificationTypeDboMapper() {}

    static NotificationType toDomain(final NotificationTypeDbo dbo) {
        return new NotificationType(
                dbo.getId(),
                dbo.getCode(),
                dbo.getName(),
                dbo.isActive(),
                new ParametersSchema(JsonMapper.shared().readTree(dbo.getParametersSchema())),
                dbo.getTemplates().stream()
                        .map(template -> new Template(
                                template.getId(), template.getChannel(), template.getSubject(), template.getBody()))
                        .collect(Collectors.toMap(Template::channel, Function.identity())));
    }
}
