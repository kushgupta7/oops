package com.bits.itsm.incident.state;

import com.bits.itsm.incident.IncidentStatus;
import static com.bits.itsm.incident.state.LifecycleActor.*;

/** Priority and SLA captured; waiting for a technician. */
final class ClassifiedState extends AbstractIncidentState {
    ClassifiedState() {
        super(IncidentStatus.CLASSIFIED);
        allow(IncidentStatus.ASSIGNED, ADMIN, SYSTEM);
    }
}
