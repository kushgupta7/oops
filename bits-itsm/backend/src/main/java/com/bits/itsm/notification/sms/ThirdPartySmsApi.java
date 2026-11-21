package com.bits.itsm.notification.sms;

import com.bits.itsm.exception.SmsDeliveryException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adaptee: a stand-in for a vendor SDK (think Twilio/MSG91) with its own, incompatible interface:
 * different method name, MSISDN format, sender id, response object and a checked exception.
 * Nothing is sent over a network; each message is written to the application log.
 */
public class ThirdPartySmsApi {
    private static final Logger log = LoggerFactory.getLogger(ThirdPartySmsApi.class);
    private final AtomicLong sequence = new AtomicLong(1000);

    /** Vendor response: status 0 = queued. */
    public record ApiResponse(int status, String messageSid, Instant acceptedAt) { }

    public ApiResponse dispatch(String msisdn, String payload, String senderId) throws SmsDeliveryException {
        if (msisdn == null || !msisdn.startsWith("91")) throw new SmsDeliveryException(21211, "Invalid destination number: " + msisdn);
        if (payload == null || payload.isBlank()) throw new SmsDeliveryException(21602, "Message body is required.");
        String sid = "SM" + sequence.incrementAndGet();
        log.info("[SIMULATED SMS] sid={} from={} to={} body=\"{}\"", sid, senderId, msisdn, payload);
        return new ApiResponse(0, sid, Instant.now());
    }
}
