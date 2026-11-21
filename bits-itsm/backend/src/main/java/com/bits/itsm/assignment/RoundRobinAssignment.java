package com.bits.itsm.assignment;

import com.bits.itsm.incident.Incident;
import com.bits.itsm.user.Technician;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Round-robin: rotates through eligible technicians in a stable order (by id), so work is spread
 * evenly regardless of load figures. One cursor is kept per required skill.
 * <p>
 * Collection choice: {@link ConcurrentHashMap} + {@link AtomicInteger} because assignments can run on
 * concurrent request threads; the cursor update must be atomic without a global lock. The cursor is
 * in memory, so it restarts at the first technician after an application restart (documented trade-off).
 */
public class RoundRobinAssignment implements AssignmentStrategy {
    private final ConcurrentHashMap<String, AtomicInteger> cursors = new ConcurrentHashMap<>();

    @Override
    public Optional<Technician> assign(Incident incident, List<Candidate> candidates) {
        List<Technician> eligible = candidates.stream().filter(c -> c.eligible(incident.requiredSkill()))
                .map(Candidate::technician).sorted(Comparator.comparing(Technician::getId)).toList();
        if (eligible.isEmpty()) return Optional.empty();
        String key = incident.requiredSkill() == null ? "ANY" : incident.requiredSkill().name();
        int turn = cursors.computeIfAbsent(key, k -> new AtomicInteger()).getAndIncrement();
        return Optional.of(eligible.get(Math.floorMod(turn, eligible.size())));
    }

    @Override public String label() { return "Round-robin"; }
}
