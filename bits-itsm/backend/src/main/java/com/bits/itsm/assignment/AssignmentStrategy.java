package com.bits.itsm.assignment;

import java.util.List;
import java.util.Optional;
import com.bits.itsm.incident.Incident;
import com.bits.itsm.user.Technician;
import com.bits.itsm.user.TechnicianSkill;

/**
 * Strategy pattern: interchangeable algorithms that pick a technician for an incident.
 * The administrator selects the active one at runtime ({@link AssignmentSettings.Mode}); the
 * service never needs to change when a new strategy is added (Open/Closed principle).
 * Every implementation must respect availability, enabled status and maximum workload via
 * {@link Candidate#eligible}.
 */
public interface AssignmentStrategy {
    /**
     * Read-only view of one technician for one incident.
     * @param resolvedCount  incidents this technician has resolved overall (experience proxy)
     * @param serviceResolutions incidents resolved on the same IT service as the incident being assigned
     */
    record Candidate(Technician technician, long workload, long weightedLoad, long resolvedCount, long serviceResolutions) {
        public Candidate(Technician technician, long workload, long weightedLoad) { this(technician, workload, weightedLoad, 0, 0); }
        public Candidate(Technician technician, long workload) { this(technician, workload, workload); }
        public boolean eligible(TechnicianSkill required) {
            return technician.canReceive(workload) && (required == null || technician.getSkills().contains(required));
        }
    }
    /** Chooses a technician, or empty when no eligible candidate exists. */
    Optional<Technician> assign(Incident incident, List<Candidate> candidates);

    /** Human-readable name used in audit records and the admin UI. */
    default String label() { return getClass().getSimpleName(); }
}
