package com.bits.itsm.assignment;

import com.bits.itsm.incident.Incident;
import com.bits.itsm.user.Technician;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Factory + registry for assignment strategies. Strategy objects are created once so stateful
 * strategies (round-robin cursors) keep their state between requests.
 * <p>
 * {@link #assignWithFallback} implements the fallback rule: try the administrator's selected strategy;
 * if it finds nobody, try the remaining strategies in a fixed order; report which one succeeded so the
 * caller can audit the fallback.
 */
@Component
public class AssignmentStrategies {
    /** Outcome of an automatic assignment, including whether a fallback strategy was needed. */
    public record Outcome(Technician technician, AssignmentSettings.Mode used, boolean fallback) { }

    private final Map<AssignmentSettings.Mode, AssignmentStrategy> strategies = new EnumMap<>(AssignmentSettings.Mode.class);
    private final SmartRecommendation smart = new SmartRecommendation();

    public AssignmentStrategies() {
        strategies.put(AssignmentSettings.Mode.SKILL_BASED, new SkillBasedAssignment());
        strategies.put(AssignmentSettings.Mode.LOAD_BASED, new LoadBasedAssignment());
        strategies.put(AssignmentSettings.Mode.ROUND_ROBIN, new RoundRobinAssignment());
        strategies.put(AssignmentSettings.Mode.PRIORITY_BASED, new PriorityBasedAssignment());
        strategies.put(AssignmentSettings.Mode.SMART, smart);
    }

    public AssignmentStrategy forMode(AssignmentSettings.Mode mode) {
        AssignmentStrategy strategy = strategies.get(mode);
        if (strategy == null) throw new IllegalArgumentException("Unknown assignment strategy " + mode);
        return strategy;
    }

    public Map<AssignmentSettings.Mode, AssignmentStrategy> all() { return Collections.unmodifiableMap(strategies); }

    public SmartRecommendation smart() { return smart; }

    public Optional<Outcome> assignWithFallback(AssignmentSettings.Mode selected, Incident incident, List<AssignmentStrategy.Candidate> candidates) {
        List<AssignmentSettings.Mode> order = new ArrayList<>();
        order.add(selected);
        for (AssignmentSettings.Mode m : AssignmentSettings.Mode.values()) if (m != selected) order.add(m);
        for (AssignmentSettings.Mode mode : order) {
            Optional<Technician> chosen = forMode(mode).assign(incident, candidates);
            if (chosen.isPresent()) return Optional.of(new Outcome(chosen.get(), mode, mode != selected));
        }
        return Optional.empty();
    }
}
