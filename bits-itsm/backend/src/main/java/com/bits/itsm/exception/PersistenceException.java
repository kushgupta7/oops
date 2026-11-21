package com.bits.itsm.exception;

/** Wraps a database failure so internals are never leaked to clients (HTTP 500). */
public class PersistenceException extends BitsolveException {
    public PersistenceException(String message) { super("PERSISTENCE_FAILURE", 500, message); }
    public PersistenceException(String message, Throwable cause) { super("PERSISTENCE_FAILURE", 500, message, cause); }
}
