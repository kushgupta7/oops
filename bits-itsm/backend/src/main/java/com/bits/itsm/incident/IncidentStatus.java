package com.bits.itsm.incident;

/**
 * Persisted lifecycle value. Behaviour for each value lives in
 * {@link com.bits.itsm.incident.state.IncidentState} implementations (State pattern).
 */
public enum IncidentStatus {
    NEW, CLASSIFIED, ASSIGNED, ACKNOWLEDGED, IN_PROGRESS, AWAITING_INFO, RESOLVED, VERIFIED, CLOSED
}
