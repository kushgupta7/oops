package com.bits.itsm.incident;

/** Test-only bridge to package-private helpers of IncidentService. */
public final class IncidentServiceAccess {
    private IncidentServiceAccess() { }
    public static SlaClock.Status worst(SlaClock.Status a, SlaClock.Status b) { return IncidentService.worst(a, b); }
}
