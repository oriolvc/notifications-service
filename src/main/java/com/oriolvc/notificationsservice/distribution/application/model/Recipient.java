package com.oriolvc.notificationsservice.distribution.application.model;

import com.oriolvc.notificationsservice.shared.domain.Channel;

/** An active member of an active list: where one notification goes on one channel. */
public record Recipient(long distributionListId, long memberId, Channel channel, String address) {}
