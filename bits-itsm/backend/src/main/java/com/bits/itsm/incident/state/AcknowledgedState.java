package com.bits.itsm.incident.state;

import com.bits.itsm.incident.IncidentStatus;
import static com.bits.itsm.incident.state.LifecycleActor.*;

/** Response SLA satisfied; investigation not yet started. */
final class AcknowledgedState extends AbstractIncidentState {
    AcknowledgedState() {
        super(IncidentStatus.ACKNOWLEDGED);
        allow(IncidentStatus.IN_PROGRESS, TECHNICIAN);
    }
    @Override public boolean countsTowardWorkload() { return true; }
}
