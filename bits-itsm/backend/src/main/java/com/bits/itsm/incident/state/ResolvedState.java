package com.bits.itsm.incident.state;

import com.bits.itsm.incident.IncidentStatus;
import static com.bits.itsm.incident.state.LifecycleActor.*;

/** Resolution recorded; the reporter verifies it or reopens it. Auto-verified by the system after 72 h without a reply. */
final class ResolvedState extends AbstractIncidentState {
    ResolvedState() {
        super(IncidentStatus.RESOLVED);
        allow(IncidentStatus.VERIFIED, STUDENT, SYSTEM);
        allow(IncidentStatus.IN_PROGRESS, STUDENT);
    }
    @Override public boolean slaClockRunning() { return false; }
}
