package com.bits.itsm.exception;

/**
 * Checked exception thrown by the (simulated) third-party SMS API. The adapter catches it and
 * converts it to the unchecked {@link ExternalServiceException}, demonstrating both kinds of
 * Java exception: checked at the external boundary, unchecked inside the domain.
 */
public class SmsDeliveryException extends Exception {
    private final int providerCode;

    public SmsDeliveryException(int providerCode, String message) {
        super(message);
        this.providerCode = providerCode;
    }

    public int providerCode() { return providerCode; }
}
