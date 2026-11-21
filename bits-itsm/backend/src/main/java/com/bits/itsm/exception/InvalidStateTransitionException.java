package com.bits.itsm.exception;

import com.bits.itsm.incident.IncidentStatus;

/**
 * Thrown by the State pattern when an incident is asked to move along an edge that its current
 * state does not allow, e.g. NEW &rarr; CLOSED (HTTP 409).
 */
public class InvalidStateTransitionException extends BitsolveException {
    private final IncidentStatus from;
    private final IncidentStatus to;

    public InvalidStateTransitionException(IncidentStatus from, IncidentStatus to) {
        super("INVALID_STATE_TRANSITION", 409, "Transition " + from + " → " + to + " is not allowed.");
        this.from = from;
        this.to = to;
    }

    /** Used when an action needs a specific state, keeping the established user-facing wording. */
    public InvalidStateTransitionException(IncidentStatus current, IncidentStatus required, String message) {
        super("INVALID_STATE_TRANSITION", 409, message);
        this.from = current;
        this.to = required;
    }

    public IncidentStatus from() { return from; }
    public IncidentStatus to() { return to; }
}
