package com.bits.itsm.incident.state;

import com.bits.itsm.incident.IncidentStatus;
import static com.bits.itsm.incident.state.LifecycleActor.*;

/** Technician is investigating. */
final class InProgressState extends AbstractIncidentState {
    InProgressState() {
        super(IncidentStatus.IN_PROGRESS);
        allow(IncidentStatus.AWAITING_INFO, TECHNICIAN);
        allow(IncidentStatus.RESOLVED, TECHNICIAN);
    }
    @Override public boolean countsTowardWorkload() { return true; }
}
