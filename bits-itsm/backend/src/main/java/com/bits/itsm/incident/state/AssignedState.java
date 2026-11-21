package com.bits.itsm.incident.state;

import com.bits.itsm.incident.IncidentStatus;
import static com.bits.itsm.incident.state.LifecycleActor.*;

/** A technician owns the incident but has not acknowledged it (response SLA running). */
final class AssignedState extends AbstractIncidentState {
    AssignedState() {
        super(IncidentStatus.ASSIGNED);
        allow(IncidentStatus.ACKNOWLEDGED, TECHNICIAN);
    }
    @Override public boolean countsTowardWorkload() { return true; }
}
