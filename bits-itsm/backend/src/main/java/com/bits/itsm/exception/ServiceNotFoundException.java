package com.bits.itsm.exception;

/** The referenced IT service does not exist or is disabled (HTTP 404). */
public class ServiceNotFoundException extends BitsolveException {
    public ServiceNotFoundException(String message) { super("SERVICE_NOT_FOUND", 404, message); }
    public ServiceNotFoundException(String message, Throwable cause) { super("SERVICE_NOT_FOUND", 404, message, cause); }
}
