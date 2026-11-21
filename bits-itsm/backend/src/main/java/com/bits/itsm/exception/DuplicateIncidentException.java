package com.bits.itsm.exception;

/** The incident was already linked or reported as a duplicate (HTTP 409). */
public class DuplicateIncidentException extends BitsolveException {
    public DuplicateIncidentException(String message) { super("DUPLICATE_INCIDENT", 409, message); }
    public DuplicateIncidentException(String message, Throwable cause) { super("DUPLICATE_INCIDENT", 409, message, cause); }
}
