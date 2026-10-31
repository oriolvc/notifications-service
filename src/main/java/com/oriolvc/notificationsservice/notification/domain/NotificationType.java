package com.oriolvc.notificationsservice.notification.domain;

/** A kind of notification. Read-only: types are created and changed by Liquibase migrations only (D-6). */
public record NotificationType(long id, String code, String name, boolean active) {}
