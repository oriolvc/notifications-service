package com.oriolvc.notificationsservice.distribution.application.model;

public record AddDistributionListMemberCommand(long tenantId, long listId, String address) {}
