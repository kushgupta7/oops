package com.bits.itsm.notification;

import java.util.List;

/**
 * Factory for the delivery channels of one notification. Each channel states its own preference
 * through {@link NotificationChannel#accepts}; the factory returns only the channels that want it
 * (for example SMS only for critical events). NotificationService therefore never contains
 * channel-specific if/else logic.
 */
public final class NotificationChannelFactory {
    private final List<NotificationChannel> available;

    public NotificationChannelFactory(List<NotificationChannel> available) {
        this.available = List.copyOf(available);
    }

    public List<NotificationChannel> channelsFor(Notification notification) {
        return available.stream().filter(channel -> channel.accepts(notification)).toList();
    }

    public List<String> names() { return available.stream().map(NotificationChannel::name).toList(); }
}
