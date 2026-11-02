package com.oriolvc.notificationsservice.notification.application;

import com.oriolvc.notificationsservice.notification.domain.Delivery;
import com.oriolvc.notificationsservice.notification.domain.SendResult;
import com.oriolvc.notificationsservice.shared.domain.Channel;

/** Port to a delivery channel. Adding a channel means adding a sender bean (RFC 4). */
public interface ChannelSender {

    Channel channel();

    /** Sends the delivery's content to its address. Never throws: every failure is a {@link SendResult}. */
    SendResult send(Delivery delivery);
}
