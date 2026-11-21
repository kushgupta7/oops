package com.bits.itsm.exception;

/** No enabled, available, qualified technician can take the incident (HTTP 409). */
public class TechnicianUnavailableException extends BitsolveException {
    public TechnicianUnavailableException(String message) { super("TECHNICIAN_UNAVAILABLE", 409, message); }
    public TechnicianUnavailableException(String message, Throwable cause) { super("TECHNICIAN_UNAVAILABLE", 409, message, cause); }
}
