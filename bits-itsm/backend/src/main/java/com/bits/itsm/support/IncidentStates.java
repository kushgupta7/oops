package com.bits.itsm.support;

import com.bits.itsm.incident.IncidentStatus;

import java.util.EnumSet;
import java.util.Set;

/** Small helpers that answer "where is this incident in its lifecycle?". */
public final class IncidentStates {

    private static final Set<IncidentStatus> ACKNOWLEDGED_OR_LATER = EnumSet.of(
            IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS, IncidentStatus.AWAITING_INFO, IncidentStatus.RESOLVED,
            IncidentStatus.VERIFIED, IncidentStatus.CLOSED);

    private static final Set<IncidentStatus> RESOLVED_OR_LATER = EnumSet.of(
            IncidentStatus.RESOLVED, IncidentStatus.VERIFIED, IncidentStatus.CLOSED);

    private IncidentStates() {
    }

    /** True once a technician has acknowledged the incident (response SLA is satisfied). */
    public static boolean isAcknowledged(IncidentSnapshot i) {
        return i.acknowledgedAt() != null || ACKNOWLEDGED_OR_LATER.contains(i.status());
    }

    /** True once the incident is resolved (resolution SLA clock has stopped). */
    public static boolean isResolved(IncidentSnapshot i) {
        return i.resolvedAt() != null || RESOLVED_OR_LATER.contains(i.status());
    }

    /** True while the resolution clock is paused because the technician is waiting for the reporter. */
    public static boolean isSlaPaused(IncidentSnapshot i) {
        return i.status() == IncidentStatus.AWAITING_INFO;
    }

    /** Open = still being worked on (not resolved, verified or closed). */
    public static boolean isOpen(IncidentSnapshot i) {
        return !isResolved(i);
    }
}
