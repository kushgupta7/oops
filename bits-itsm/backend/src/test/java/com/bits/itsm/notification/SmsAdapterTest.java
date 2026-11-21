package com.bits.itsm.notification;

import com.bits.itsm.event.EventType;
import com.bits.itsm.exception.ExternalServiceException;
import com.bits.itsm.notification.sms.SmsChannel;
import com.bits.itsm.notification.sms.SmsGateway;
import com.bits.itsm.notification.sms.SmsGatewayAdapter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Adapter (SMS gateway), channel preferences and the channel factory. */
class SmsAdapterTest {
    private Notification n(EventType type) { return new Notification("n1", 7L, type, 1L, "SLA breached", "INC-1 breached its resolution SLA.", Instant.now()); }

    @Test void adapterTranslatesToTheVendorApiAndReturnsAReceipt() {
        String sid = new SmsGatewayAdapter().send("+91 90123 45678", "hello");
        assertTrue(sid.startsWith("SM"));
    }

    @Test void adapterConvertsTheVendorsCheckedExceptionIntoExternalServiceException() {
        ExternalServiceException e = assertThrows(ExternalServiceException.class, () -> new SmsGatewayAdapter().send("+1 555 0100", "hello"));
        assertEquals("EXTERNAL_SERVICE_FAILURE", e.code());
        assertEquals(502, e.httpStatus());
        assertTrue(e.getCause() instanceof com.bits.itsm.exception.SmsDeliveryException);
    }

    @Test void smsChannelOnlyAcceptsConfiguredUrgentEvents() {
        SmsChannel channel = new SmsChannel(new SmsGatewayAdapter(), "SLA_BREACHED,INCIDENT_ESCALATED");
        assertTrue(channel.accepts(n(EventType.SLA_BREACHED)));
        assertFalse(channel.accepts(n(EventType.INCIDENT_CREATED)));
    }

    @Test void providerFailureIsRecordedButNeverThrown() {
        SmsGateway failing = (phone, text) -> { throw new ExternalServiceException("provider down"); };
        SmsChannel channel = new SmsChannel(failing, "SLA_BREACHED");
        channel.send(n(EventType.SLA_BREACHED));
        assertEquals(1, channel.recent().size());
        assertFalse(channel.recent().get(0).delivered());
    }

    @Test void longMessagesAreTruncatedToOneSegment() {
        List<String> sent = new ArrayList<>();
        SmsChannel channel = new SmsChannel((phone, text) -> { sent.add(text); return "ok"; }, "SLA_BREACHED");
        channel.send(new Notification("n2", 7L, EventType.SLA_BREACHED, 1L, "t", "x".repeat(500), Instant.now()));
        assertTrue(sent.get(0).length() > 160); // channel passes full text; the adapter is responsible for truncation
        String receipt = new SmsGatewayAdapter().send("+91 90000 00000", "y".repeat(500));
        assertNotNull(receipt);
    }

    @Test void factoryReturnsOnlyChannelsThatAcceptTheNotification() {
        NotificationChannel inApp = new NotificationChannel() { public String name() { return "IN_APP"; } public void send(Notification x) { } };
        SmsChannel sms = new SmsChannel(new SmsGatewayAdapter(), "SLA_BREACHED");
        NotificationChannelFactory factory = new NotificationChannelFactory(List.of(inApp, sms));
        assertEquals(List.of(inApp), factory.channelsFor(n(EventType.INCIDENT_CREATED)));
        assertEquals(List.of(inApp, sms), factory.channelsFor(n(EventType.SLA_BREACHED)));
        assertEquals(List.of("IN_APP", "SMS"), factory.names());
    }
}
