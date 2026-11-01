package com.oriolvc.notificationsservice.notification.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Row of {@code notification_types}; read-only (D-6). */
@Entity
@Immutable
@Table(name = "notification_types")
class NotificationTypeDbo {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active;

    /** The JSON Schema as text ({@code jsonb} column); parsed by the mapper. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters_schema", nullable = false)
    private String parametersSchema;

    @OneToMany(mappedBy = "notificationType")
    private List<NotificationTemplateDbo> templates = new ArrayList<>();

    Long getId() {
        return id;
    }

    String getCode() {
        return code;
    }

    String getName() {
        return name;
    }

    boolean isActive() {
        return active;
    }

    String getParametersSchema() {
        return parametersSchema;
    }

    List<NotificationTemplateDbo> getTemplates() {
        return templates;
    }
}
