package com.bits.itsm.exception;

/** Wraps a failure of a third-party system (SMS gateway, SMTP, LLM API) (HTTP 502). */
public class ExternalServiceException extends BitsolveException {
    public ExternalServiceException(String message) { super("EXTERNAL_SERVICE_FAILURE", 502, message); }
    public ExternalServiceException(String message, Throwable cause) { super("EXTERNAL_SERVICE_FAILURE", 502, message, cause); }
}
