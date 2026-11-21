package com.bits.itsm.notification.sms;

/** Target interface the application wants: send a short text to a phone number, return a receipt id. */
public interface SmsGateway {
    String send(String phoneNumber, String text);
}
