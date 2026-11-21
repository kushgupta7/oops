package com.bits.itsm.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates every {@link BitsolveException} thrown anywhere below a controller into the same JSON
 * shape. Controllers therefore contain no error-handling code (polymorphism: one handler serves
 * all subclasses through the {@code code()/httpStatus()} methods).
 */
@RestControllerAdvice
public class GlobalApiErrors {
    private static final Logger log = LoggerFactory.getLogger(GlobalApiErrors.class);

    /** Database failure: wrapped so driver/SQL details never reach the client. */
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    ResponseEntity<ApiError> database(org.springframework.dao.DataAccessException error, HttpServletRequest request) {
        return domain(new PersistenceException("Database operation failed.", error), request);
    }

    @ExceptionHandler(BitsolveException.class)
    ResponseEntity<ApiError> domain(BitsolveException error, HttpServletRequest request) {
        if (error.httpStatus() >= 500) log.error("{} on {}", error.code(), request.getRequestURI(), error);
        // 5xx messages are replaced so database/driver details never reach the browser.
        String message = error.httpStatus() >= 500 ? "The request could not be completed. Please retry shortly." : error.getMessage();
        return ResponseEntity.status(error.httpStatus()).body(ApiError.of(error.httpStatus(), error.code(), message, request.getRequestURI()));
    }
}
