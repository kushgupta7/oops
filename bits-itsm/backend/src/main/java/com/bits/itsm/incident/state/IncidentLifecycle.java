package com.bits.itsm.incident.state;

import com.bits.itsm.incident.IncidentStatus;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Registry (flyweight) of the stateless state objects: one shared instance per {@link IncidentStatus}.
 * The JPA entity persists only the enum; this class maps it back to behaviour.
 */
public final class IncidentLifecycle {
    private static final Map<IncidentStatus, IncidentState> STATES;

    static {
        Map<IncidentStatus, IncidentState> states = new EnumMap<>(IncidentStatus.class);
        for (IncidentState state : new IncidentState[] {
                new NewState(), new ClassifiedState(), new AssignedState(), new AcknowledgedState(),
                new InProgressState(), new AwaitingInfoState(), new ResolvedState(), new VerifiedState(), new ClosedState() }) {
            states.put(state.status(), state);
        }
        for (IncidentStatus status : IncidentStatus.values()) {
            if (!states.containsKey(status)) throw new ExceptionInInitializerError("No state class for " + status);
        }
        STATES = Collections.unmodifiableMap(states);
    }

    private IncidentLifecycle() { }

    public static IncidentState of(IncidentStatus status) {
        return STATES.get(java.util.Objects.requireNonNull(status, "status"));
    }

    /** Whole transition table, used by the API/docs to show the state diagram. */
    public static Map<IncidentStatus, IncidentState> all() { return STATES; }
}
