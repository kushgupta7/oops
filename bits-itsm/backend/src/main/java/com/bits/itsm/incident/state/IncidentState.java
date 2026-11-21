package com.bits.itsm.incident.state;

import com.bits.itsm.incident.IncidentStatus;
import java.util.Set;

/**
 * State pattern: one implementation per lifecycle state. Each state alone decides
 * <ul>
 *   <li>which next states are reachable ({@link #allowedTransitions()}),</li>
 *   <li>which actors may trigger each edge ({@link #actorsFor(IncidentStatus)}),</li>
 *   <li>state-specific behaviour such as whether the SLA clock runs or the incident counts toward workload.</li>
 * </ul>
 * {@code Incident} delegates every status change to {@link #requireTransition}; an illegal edge
 * throws {@link com.bits.itsm.exception.InvalidStateTransitionException}. Adding a new state means
 * adding one class and one registry entry; no {@code if/else} chains elsewhere need editing.
 */
public interface IncidentState {
    /** The persisted enum value this state object represents. */
    IncidentStatus status();

    /** All states reachable in one step. */
    Set<IncidentStatus> allowedTransitions();

    /** Actors permitted to perform the transition to {@code target}; empty if the edge does not exist. */
    Set<LifecycleActor> actorsFor(IncidentStatus target);

    /** True while the resolution SLA clock is counting (false while waiting for the reporter). */
    boolean slaClockRunning();

    /** True when a technician is actively responsible, so the incident counts toward workload. */
    boolean countsTowardWorkload();

    /** True when no further transitions exist. */
    default boolean isTerminal() { return allowedTransitions().isEmpty(); }

    default boolean canTransitionTo(IncidentStatus target) { return allowedTransitions().contains(target); }

    /** Validates the edge (any actor). */
    void requireTransition(IncidentStatus target);

    /** Validates the edge and that {@code actor} may trigger it. */
    void requireTransition(IncidentStatus target, LifecycleActor actor);
}
