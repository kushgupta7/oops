package com.bits.itsm.exception;

/**
 * Root of BITSolve's own exception hierarchy (OOP concept: inheritance + abstraction).
 * <p>
 * Every subclass carries a stable machine-readable {@link #code()} and the HTTP status that
 * {@link GlobalApiErrors} returns, so controllers never need try/catch blocks.
 * The class is abstract: callers must throw a specific subtype that says what went wrong.
 * It is unchecked because business-rule violations cannot be "recovered" by the caller and
 * must roll back the surrounding transaction; {@link SmsDeliveryException} is the checked
 * counterpart used at the boundary with an external system.
 */
public abstract class BitsolveException extends RuntimeException {
    private final String code;
    private final int httpStatus;

    protected BitsolveException(String code, int httpStatus, String message) {
        this(code, httpStatus, message, null);
    }

    protected BitsolveException(String code, int httpStatus, String message, Throwable cause) {
        super(message, cause);
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Exception code is required.");
        if (httpStatus < 400 || httpStatus > 599) throw new IllegalArgumentException("HTTP status must be an error status.");
        this.code = code;
        this.httpStatus = httpStatus;
    }

    /** Stable error code, e.g. INVALID_STATE_TRANSITION, shown to API clients. */
    public String code() { return code; }

    /** HTTP status returned to the client. */
    public int httpStatus() { return httpStatus; }
}
