package com.bits.itsm.incident.state;

import com.bits.itsm.incident.IncidentStatus;
import static com.bits.itsm.incident.state.LifecycleActor.*;

/** Terminal state. */
final class ClosedState extends AbstractIncidentState {
    ClosedState() {
        super(IncidentStatus.CLOSED);

    }
    @Override public boolean slaClockRunning() { return false; }
}
