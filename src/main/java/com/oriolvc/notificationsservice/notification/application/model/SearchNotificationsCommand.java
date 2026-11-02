package com.oriolvc.notificationsservice.notification.application.model;

/**
 * @param eventKey optional filter
 * @param customerId optional filter
 * @param tenantId the caller's tenant; {@code null} for publishers, who read every tenant
 */
public record SearchNotificationsCommand(String eventKey, Long customerId, Long tenantId) {}
