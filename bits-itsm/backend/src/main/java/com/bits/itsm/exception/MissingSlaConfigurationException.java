package com.bits.itsm.exception;

/** No SLA policy exists for the incident's priority (HTTP 409). */
public class MissingSlaConfigurationException extends BitsolveException {
    public MissingSlaConfigurationException(String message) { super("MISSING_SLA_CONFIGURATION", 409, message); }
    public MissingSlaConfigurationException(String message, Throwable cause) { super("MISSING_SLA_CONFIGURATION", 409, message, cause); }
}
