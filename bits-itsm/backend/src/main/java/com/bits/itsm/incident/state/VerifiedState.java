package com.bits.itsm.incident.state;

import com.bits.itsm.incident.IncidentStatus;
import static com.bits.itsm.incident.state.LifecycleActor.*;

/** Reporter confirmed the fix; an administrator (or the auto-closer) closes it. */
final class VerifiedState extends AbstractIncidentState {
    VerifiedState() {
        super(IncidentStatus.VERIFIED);
        allow(IncidentStatus.CLOSED, ADMIN, SYSTEM);
    }
    @Override public boolean slaClockRunning() { return false; }
}
