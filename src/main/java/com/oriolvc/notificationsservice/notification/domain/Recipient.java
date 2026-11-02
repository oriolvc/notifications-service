package com.oriolvc.notificationsservice.notification.domain;

import com.oriolvc.notificationsservice.shared.domain.Channel;

/** An active member of one of the customer's active lists: where one notification goes on one channel. */
public record Recipient(long distributionListId, long memberId, Channel channel, String address) {}
