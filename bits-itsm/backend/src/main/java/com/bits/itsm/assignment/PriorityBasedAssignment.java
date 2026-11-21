package com.bits.itsm.assignment;

import com.bits.itsm.incident.Incident;
import com.bits.itsm.user.Technician;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Priority-based: critical work (P1, and P2) goes to the most experienced eligible <em>senior</em>
 * technician (at least {@link #DEFAULT_SENIOR_THRESHOLD} resolved incidents; ties broken by lower load, then id).
 * If no senior technician is free the strategy returns empty and {@link AssignmentStrategies} falls back
 * to the next strategy, which is audited. P3/P4 delegate to {@link LoadBasedAssignment} so senior staff
 * are not flooded with routine tickets.
 * Composition: this strategy reuses another strategy object instead of duplicating its logic.
 */
public class PriorityBasedAssignment implements AssignmentStrategy {
    public static final long DEFAULT_SENIOR_THRESHOLD = 3;
    private final AssignmentStrategy routine;
    private final long seniorThreshold;

    public PriorityBasedAssignment() { this(new LoadBasedAssignment(), DEFAULT_SENIOR_THRESHOLD); }

    public PriorityBasedAssignment(AssignmentStrategy routine, long seniorThreshold) {
        if (seniorThreshold < 0) throw new IllegalArgumentException("Seniority threshold cannot be negative.");
        this.routine = java.util.Objects.requireNonNull(routine);
        this.seniorThreshold = seniorThreshold;
    }

    @Override
    public Optional<Technician> assign(Incident incident, List<Candidate> candidates) {
        Incident.Priority p = incident.getPriority();
        if (p != Incident.Priority.P1 && p != Incident.Priority.P2) return routine.assign(incident, candidates);
        return candidates.stream().filter(c -> c.eligible(incident.requiredSkill()) && c.resolvedCount() >= seniorThreshold)
                .max(Comparator.comparingLong(Candidate::resolvedCount)
                        .thenComparing(Comparator.comparingLong(Candidate::weightedLoad).reversed())
                        .thenComparing(Comparator.comparing((Candidate c) -> c.technician().getId()).reversed()))
                .map(Candidate::technician);
    }

    @Override public String label() { return "Priority-based"; }
}
