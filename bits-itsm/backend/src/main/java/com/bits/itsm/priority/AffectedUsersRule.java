package com.bits.itsm.priority;

import com.bits.itsm.incident.Incident.Priority;

/**
 * "Number of affected users" priority factor (brief: Intelligent Priority Determination).
 * Applied after whichever {@link PriorityStrategy} is active, so it works with all four strategies:
 * when more than {@link #THRESHOLD} people are affected the priority is raised by one level (P1 stays P1).
 * Decorator-style post-processing keeps the strategies themselves unchanged.
 */
public final class AffectedUsersRule {
    public static final int THRESHOLD = 100;

    private AffectedUsersRule() { }

    public static Priority apply(Priority calculated, Integer affectedUsers) {
        if (calculated == null || calculated == Priority.PENDING) throw new IllegalArgumentException("A calculated priority is required.");
        if (affectedUsers == null || affectedUsers <= THRESHOLD || calculated == Priority.P1) return calculated;
        return Priority.values()[calculated.ordinal() - 1];
    }
}
