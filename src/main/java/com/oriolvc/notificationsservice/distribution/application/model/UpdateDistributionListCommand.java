package com.oriolvc.notificationsservice.distribution.application.model;

/**
 * @param name new name, or {@code null} to keep it
 * @param active new state, or {@code null} to keep it
 * @param version the version the client last read
 */
public record UpdateDistributionListCommand(long tenantId, long listId, String name, Boolean active, long version) {}
