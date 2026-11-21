package com.bits.itsm.incident;

import com.bits.itsm.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.*;
import com.bits.itsm.assignment.AssignmentController;

/**
 * Maps JDK/Spring exceptions raised by the operational controllers to the same
 * {timestamp, status, code, message, path} body produced for BitsolveException by
 * {@link com.bits.itsm.exception.GlobalApiErrors}. The "message" field is unchanged for the React client.
 */
@RestControllerAdvice(assignableTypes = {com.bits.itsm.api.AdminManagementController.class, com.bits.itsm.api.ServicesController.class, IncidentController.class, AssignmentController.class, SlaController.class, com.bits.itsm.priority.PriorityController.class, com.bits.itsm.api.InsightsController.class})
public class IncidentApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> accessError(ResponseStatusException error, HttpServletRequest request) {
        int status = error.getStatusCode().value();
        String code = status == 401 ? "UNAUTHENTICATED" : status == 403 ? "FORBIDDEN" : status == 404 ? "NOT_FOUND" : "REQUEST_FAILED";
        return ResponseEntity.status(status).body(ApiError.of(status, code, error.getReason() == null ? "Request could not be completed." : error.getReason(), request.getRequestURI()));
    }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiError> conflict(IllegalStateException error, HttpServletRequest request) {
        return ResponseEntity.status(409).body(ApiError.of(409, "BUSINESS_RULE_CONFLICT", error.getMessage(), request.getRequestURI()));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> invalid(IllegalArgumentException error, HttpServletRequest request) {
        return ResponseEntity.status(400).body(ApiError.of(400, "INVALID_ARGUMENT", error.getMessage(), request.getRequestURI()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> malformed(HttpServletRequest request) {
        return ResponseEntity.status(400).body(ApiError.of(400, "VALIDATION_FAILED", "Check required fields, allowed options and text lengths.", request.getRequestURI()));
    }
}
