package com.bits.itsm.exception;

/** Wrong e-mail/password or a disabled account (HTTP 401). */
public class InvalidLoginException extends BitsolveException {
    public InvalidLoginException(String message) { super("INVALID_LOGIN", 401, message); }
    public InvalidLoginException(String message, Throwable cause) { super("INVALID_LOGIN", 401, message, cause); }
}
