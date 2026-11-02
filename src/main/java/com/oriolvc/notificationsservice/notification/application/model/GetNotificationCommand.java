package com.oriolvc.notificationsservice.notification.application.model;

/** @param tenantId the caller's tenant; {@code null} for publishers, who read every tenant */
public record GetNotificationCommand(long notificationId, Long tenantId) {}
