package com.bits.itsm.exception;

/** Request values fail business validation beyond Bean Validation (HTTP 400). */
public class ValidationException extends BitsolveException {
    public ValidationException(String message) { super("VALIDATION_FAILED", 400, message); }
    public ValidationException(String message, Throwable cause) { super("VALIDATION_FAILED", 400, message, cause); }
}
