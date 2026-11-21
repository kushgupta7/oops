package com.bits.itsm.incident.state;

import com.bits.itsm.user.Role;

/** Who is attempting a lifecycle transition. SYSTEM is used by schedulers (auto-close, auto-assign). */
public enum LifecycleActor {
    STUDENT, TECHNICIAN, ADMIN, SYSTEM;

    public static LifecycleActor of(Role role) {
        if (role == null) return SYSTEM;
        return switch (role) {
            case STUDENT -> STUDENT;
            case TECHNICIAN -> TECHNICIAN;
            case ADMIN -> ADMIN;
        };
    }
}
