package com.bits.itsm.exception;

/** Incident data breaks a domain rule (missing service, bad text length, unknown category) (HTTP 400). */
public class InvalidIncidentException extends BitsolveException {
    public InvalidIncidentException(String message) { super("INVALID_INCIDENT", 400, message); }
    public InvalidIncidentException(String message, Throwable cause) { super("INVALID_INCIDENT", 400, message, cause); }
}
