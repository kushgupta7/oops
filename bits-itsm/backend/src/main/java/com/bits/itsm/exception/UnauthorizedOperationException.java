package com.bits.itsm.exception;

/** The signed-in role may not perform this action or transition (HTTP 403). */
public class UnauthorizedOperationException extends BitsolveException {
    public UnauthorizedOperationException(String message) { super("UNAUTHORIZED_OPERATION", 403, message); }
    public UnauthorizedOperationException(String message, Throwable cause) { super("UNAUTHORIZED_OPERATION", 403, message, cause); }
}
