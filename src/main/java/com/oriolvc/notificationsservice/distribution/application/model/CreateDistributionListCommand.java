package com.oriolvc.notificationsservice.distribution.application.model;

import com.oriolvc.notificationsservice.shared.domain.Channel;

/** @param tenantId the caller's tenant, from the JWT */
public record CreateDistributionListCommand(long tenantId, String name, String notificationType, Channel channel) {}
