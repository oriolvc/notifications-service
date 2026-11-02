package com.oriolvc.notificationsservice.notification.application;

import com.oriolvc.notificationsservice.notification.application.ports.ChannelSender;
import com.oriolvc.notificationsservice.shared.domain.Channel;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** The {@link ChannelSender} of each channel. */
@Component
class ChannelSenders {

    private final Map<Channel, ChannelSender> byChannel = new EnumMap<>(Channel.class);

    ChannelSenders(final List<ChannelSender> senders) {
        for (final ChannelSender sender : senders) {
            if (byChannel.put(sender.channel(), sender) != null) {
                throw new IllegalStateException("Two channel senders for " + sender.channel());
            }
        }
    }

    ChannelSender forChannel(final Channel channel) {
        final ChannelSender sender = byChannel.get(channel);
        if (sender == null) {
            throw new IllegalStateException("No channel sender for " + channel);
        }
        return sender;
    }
}
