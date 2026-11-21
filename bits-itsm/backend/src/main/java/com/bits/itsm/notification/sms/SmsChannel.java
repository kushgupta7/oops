package com.bits.itsm.notification.sms;

import com.bits.itsm.event.EventType;
import com.bits.itsm.exception.ExternalServiceException;
import com.bits.itsm.notification.Notification;
import com.bits.itsm.notification.NotificationChannel;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Simulated SMS channel. Preference: only urgent events (configurable through {@code itsm.sms.events}) go
 * by SMS, so users are not spammed. Delivery is best-effort: a provider failure is logged and never rolls
 * back the business transaction. Campus accounts have no phone numbers yet, so a deterministic simulated
 * number per user is used (documented limitation).
 */
@Component
public class SmsChannel implements NotificationChannel {
    private static final Logger log = LoggerFactory.getLogger(SmsChannel.class);
    private static final int HISTORY = 50;

    /** One simulated message, kept in memory so the admin UI can show what would have been texted. */
    public record SentSms(Instant at, Long recipientId, String phone, String text, String receipt, boolean delivered) { }

    private final SmsGateway gateway;
    private final Set<EventType> events;
    private final ConcurrentLinkedDeque<SentSms> recent = new ConcurrentLinkedDeque<>();

    public SmsChannel(SmsGateway gateway, @Value("${itsm.sms.events:SLA_BREACHED,INCIDENT_ESCALATED,MAJOR_INCIDENT_DETECTED}") String events) {
        this.gateway = gateway;
        EnumSet<EventType> parsed = EnumSet.noneOf(EventType.class);
        Arrays.stream(events.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(EventType::valueOf).forEach(parsed::add);
        this.events = parsed;
    }

    @Override public String name() { return "SMS"; }

    @Override public boolean accepts(Notification notification) { return events.contains(notification.type()); }

    @Override
    public void send(Notification n) {
        String phone = simulatedNumber(n.recipientId());
        String text = "BITSolve: " + n.title() + ". " + n.message();
        try {
            String receipt = gateway.send(phone, text);
            remember(new SentSms(Instant.now(), n.recipientId(), phone, text, receipt, true));
        } catch (ExternalServiceException ex) {
            log.warn("SMS to user {} failed: {}", n.recipientId(), ex.getMessage());
            remember(new SentSms(Instant.now(), n.recipientId(), phone, text, null, false));
        }
    }

    public List<SentSms> recent() { return List.copyOf(recent); }

    static String simulatedNumber(Long userId) {
        long suffix = 10_000_000L + Math.floorMod(userId == null ? 0 : userId, 90_000_000L);
        return "+91 90" + suffix;
    }

    private void remember(SentSms sms) {
        recent.addFirst(sms);
        while (recent.size() > HISTORY) recent.pollLast();
    }
}
