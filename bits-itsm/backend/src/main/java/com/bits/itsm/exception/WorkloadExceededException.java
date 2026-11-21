package com.bits.itsm.exception;

/** The selected technician is already at maximum workload (HTTP 409). */
public class WorkloadExceededException extends BitsolveException {
    public WorkloadExceededException(String message) { super("WORKLOAD_EXCEEDED", 409, message); }
    public WorkloadExceededException(String message, Throwable cause) { super("WORKLOAD_EXCEEDED", 409, message, cause); }
}
