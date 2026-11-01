package com.oriolvc.notificationsservice.distribution.application.model;

public record RemoveDistributionListMemberCommand(long tenantId, long listId, long memberId) {}
