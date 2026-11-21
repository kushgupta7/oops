package com.bits.itsm.assignment;

import com.bits.itsm.incident.Incident;
import com.bits.itsm.user.Technician;
import com.bits.itsm.user.TechnicianSkill;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Intelligent feature D, Smart Technician Recommendation. Ranks every eligible technician by a weighted score:
 * <pre>
 *   score = 0.35·skill + 0.25·(1 − load/capacity) + 0.20·experience + 0.20·serviceHistory
 * </pre>
 * skill = 1 if the technician has the required or preferred skill (0.5 for a general incident with no preference),
 * experience = resolved incidents normalised by the most experienced candidate, serviceHistory = past resolutions
 * on the same IT service normalised the same way. Availability and capacity are hard filters, not weights.
 * Each recommendation explains its score so the administrator can trust or override it.
 */
public class SmartRecommendation implements AssignmentStrategy {
    public static final double SKILL_WEIGHT = 0.35, LOAD_WEIGHT = 0.25, EXPERIENCE_WEIGHT = 0.20, SERVICE_WEIGHT = 0.20;

    /** One ranked technician with the reasons behind the score. */
    public record Recommendation(Long technicianId, String name, double score, long workload, int maxWorkload, List<String> reasons) { }

    @Override
    public Optional<Technician> assign(Incident incident, List<Candidate> candidates) {
        List<Recommendation> ranked = rank(incident, candidates);
        if (ranked.isEmpty()) return Optional.empty();
        Long best = ranked.get(0).technicianId();
        return candidates.stream().map(Candidate::technician).filter(t -> t.getId().equals(best)).findFirst();
    }

    /** All eligible technicians, best first. */
    public List<Recommendation> rank(Incident incident, List<Candidate> candidates) {
        TechnicianSkill required = incident.requiredSkill();
        TechnicianSkill preferred = incident.preferredSkill();
        List<Candidate> eligible = candidates.stream().filter(c -> c.eligible(required)).toList();
        long maxResolved = Math.max(1, eligible.stream().mapToLong(Candidate::resolvedCount).max().orElse(1));
        long maxService = Math.max(1, eligible.stream().mapToLong(Candidate::serviceResolutions).max().orElse(1));
        List<Recommendation> result = new ArrayList<>();
        for (Candidate c : eligible) {
            Technician t = c.technician();
            List<String> reasons = new ArrayList<>();
            double skill;
            if (preferred != null && t.getSkills().contains(preferred)) { skill = 1.0; reasons.add("Has " + label(preferred) + " skill"); }
            else if (preferred == null) { skill = 0.5; reasons.add("General incident: no specific skill needed"); }
            else { skill = 0.0; reasons.add("Lacks preferred " + label(preferred) + " skill"); }
            double load = 1.0 - Math.min(1.0, (double) c.workload() / t.getMaxWorkload());
            reasons.add("Workload " + c.workload() + "/" + t.getMaxWorkload());
            double experience = (double) c.resolvedCount() / maxResolved;
            reasons.add(c.resolvedCount() + " incident(s) resolved overall");
            double service = (double) c.serviceResolutions() / maxService;
            if (c.serviceResolutions() > 0 && incident.getAffectedService() != null) reasons.add(c.serviceResolutions() + " past fix(es) on " + incident.getAffectedService().getLabel());
            double score = SKILL_WEIGHT * skill + LOAD_WEIGHT * load + EXPERIENCE_WEIGHT * experience + SERVICE_WEIGHT * service;
            result.add(new Recommendation(t.getId(), t.getName(), Math.round(score * 1000) / 1000.0, c.workload(), t.getMaxWorkload(), List.copyOf(reasons)));
        }
        result.sort(Comparator.comparingDouble(Recommendation::score).reversed().thenComparing(Recommendation::technicianId));
        return result;
    }

    /** The top {@code limit} recommendations (the UI shows three). */
    public List<Recommendation> top(Incident incident, List<Candidate> candidates, int limit) {
        List<Recommendation> ranked = rank(incident, candidates);
        return ranked.subList(0, Math.min(limit, ranked.size()));
    }

    private static String label(TechnicianSkill skill) { return skill.name().replace('_', ' ').toLowerCase(Locale.ROOT); }

    @Override public String label() { return "Smart recommendation"; }
}
