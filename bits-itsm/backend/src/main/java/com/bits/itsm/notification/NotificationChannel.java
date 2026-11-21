package com.bits.itsm.notification;

/**
 * Contract for a delivery mechanism (in-app, SMS, push...). Add a channel = add one @Component class;
 * {@link NotificationChannelFactory} picks it up automatically.
 */
public interface NotificationChannel {
    String name();

    void send(Notification notification);

    /** Preference hook: whether this channel should carry the given notification. Default: every notification. */
    default boolean accepts(Notification notification) { return true; }
}
