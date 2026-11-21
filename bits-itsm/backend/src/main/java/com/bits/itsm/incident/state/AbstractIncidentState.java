package com.bits.itsm.incident.state;

import com.bits.itsm.exception.InvalidStateTransitionException;
import com.bits.itsm.exception.UnauthorizedOperationException;
import com.bits.itsm.incident.IncidentStatus;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Shared behaviour for concrete states (abstraction + inheritance).
 * The transition table is an {@link EnumMap}: keys are a small fixed enum, so an array-backed map
 * gives O(1) lookup, natural ordering and no hashing overhead.
 */
abstract class AbstractIncidentState implements IncidentState {
    private final IncidentStatus status;
    private final Map<IncidentStatus, Set<LifecycleActor>> transitions = new EnumMap<>(IncidentStatus.class);

    protected AbstractIncidentState(IncidentStatus status) {
        this.status = status;
    }

    /** Subclasses declare their outgoing edges in their constructor. */
    protected final void allow(IncidentStatus target, LifecycleActor first, LifecycleActor... rest) {
        transitions.put(target, Collections.unmodifiableSet(EnumSet.of(first, rest)));
    }

    @Override public final IncidentStatus status() { return status; }

    @Override public final Set<IncidentStatus> allowedTransitions() {
        return transitions.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(transitions.keySet()));
    }

    @Override public final Set<LifecycleActor> actorsFor(IncidentStatus target) {
        return transitions.getOrDefault(target, Set.of());
    }

    @Override public boolean slaClockRunning() { return true; }

    @Override public boolean countsTowardWorkload() { return false; }

    @Override public final void requireTransition(IncidentStatus target) {
        if (!canTransitionTo(target)) throw new InvalidStateTransitionException(status, target);
    }

    @Override public final void requireTransition(IncidentStatus target, LifecycleActor actor) {
        requireTransition(target);
        if (!actorsFor(target).contains(actor)) {
            throw new UnauthorizedOperationException(actor + " may not move an incident from " + status + " to " + target + ".");
        }
    }

    @Override public String toString() { return status.name(); }
}
