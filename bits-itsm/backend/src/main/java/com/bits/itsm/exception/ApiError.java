package com.bits.itsm.exception;

import java.time.Instant;

/** Consistent JSON error body: {timestamp, status, code, message, path}. */
public record ApiError(Instant timestamp, int status, String code, String message, String path) {
    public static ApiError of(int status, String code, String message, String path) {
        return new ApiError(Instant.now(), status, code, message, path);
    }
}
