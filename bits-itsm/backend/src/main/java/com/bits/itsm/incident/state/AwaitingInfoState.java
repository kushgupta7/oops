package com.bits.itsm.incident.state;

import com.bits.itsm.incident.IncidentStatus;
import static com.bits.itsm.incident.state.LifecycleActor.*;

/** Technician asked the reporter a question; the resolution SLA clock is paused until the reply. */
final class AwaitingInfoState extends AbstractIncidentState {
    AwaitingInfoState() {
        super(IncidentStatus.AWAITING_INFO);
        allow(IncidentStatus.IN_PROGRESS, STUDENT);
    }
    /** The defining behaviour of this state: time spent waiting for the reporter is not charged to IT. */
    @Override public boolean slaClockRunning() { return false; }
    @Override public boolean countsTowardWorkload() { return true; }
}
