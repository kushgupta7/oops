package com.bits.itsm.notification.sms;

import com.bits.itsm.exception.ExternalServiceException;
import com.bits.itsm.exception.SmsDeliveryException;
import org.springframework.stereotype.Component;

/**
 * Adapter pattern: makes {@link ThirdPartySmsApi} look like our {@link SmsGateway}.
 * It converts "+91 98765 43210" into the vendor's MSISDN format "919876543210", truncates text to one
 * 160-character SMS segment, supplies the sender id, and turns the vendor's checked exception into our
 * unchecked {@link ExternalServiceException}. Swapping vendors means writing another adapter only.
 */
@Component
public class SmsGatewayAdapter implements SmsGateway {
    static final int SEGMENT = 160;
    private static final String SENDER_ID = "BITSLV";
    private final ThirdPartySmsApi api;

    public SmsGatewayAdapter() { this(new ThirdPartySmsApi()); }

    SmsGatewayAdapter(ThirdPartySmsApi api) { this.api = api; }

    @Override
    public String send(String phoneNumber, String text) {
        String msisdn = phoneNumber == null ? null : phoneNumber.replaceAll("[^0-9]", "");
        String body = text == null ? "" : text.length() <= SEGMENT ? text : text.substring(0, SEGMENT - 1) + "…";
        try {
            return api.dispatch(msisdn, body, SENDER_ID).messageSid();
        } catch (SmsDeliveryException e) {
            throw new ExternalServiceException("SMS provider rejected the message (code " + e.providerCode() + ").", e);
        }
    }

    /** Exposed for tests: the vendor-format number the adapter would produce. */
    static String toMsisdn(String phoneNumber) { return phoneNumber.replaceAll("[^0-9]", ""); }
}
