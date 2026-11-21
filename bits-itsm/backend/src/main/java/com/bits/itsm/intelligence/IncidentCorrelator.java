package com.bits.itsm.intelligence;

import com.bits.itsm.incident.Incident;
import com.bits.itsm.support.IncidentLabel;
import com.bits.itsm.support.IncidentSnapshot;
import com.bits.itsm.support.IncidentStates;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Intelligent feature B, Incident Correlation. When at least {@code minimum} open incidents hit the same IT
 * service within {@code window}, they are grouped into a "major incident" (problem record) so one team works on
 * the root cause instead of many technicians chasing symptoms. Greedy windowing over incidents sorted by time.
 */
public class IncidentCorrelator {
    public record MajorIncident(Long anchorIncidentId, String anchorLabel, Incident.ItService service, String serviceLabel,
                                List<Long> incidentIds, List<String> labels, Instant firstReported, Instant lastReported) {
        public int size() { return incidentIds.size(); }
    }

    public static final Duration DEFAULT_WINDOW = Duration.ofMinutes(30);
    public static final int DEFAULT_MINIMUM = 3;
    private final Duration window;
    private final int minimum;

    public IncidentCorrelator() { this(DEFAULT_WINDOW, DEFAULT_MINIMUM); }

    public IncidentCorrelator(Duration window, int minimum) {
        if (window.isNegative() || window.isZero() || minimum < 2) throw new IllegalArgumentException("Window must be positive and minimum at least 2.");
        this.window = window;
        this.minimum = minimum;
    }

    public List<MajorIncident> correlate(Collection<IncidentSnapshot> incidents) {
        Map<Incident.ItService, List<IncidentSnapshot>> byService = new EnumMap<>(Incident.ItService.class);
        for (IncidentSnapshot i : incidents) {
            if (i.service() == null || i.service() == Incident.ItService.OTHER || !IncidentStates.isOpen(i)) continue;
            byService.computeIfAbsent(i.service(), s -> new ArrayList<>()).add(i);
        }
        List<MajorIncident> result = new ArrayList<>();
        byService.forEach((service, list) -> {
            list.sort(Comparator.comparing(IncidentSnapshot::createdAt).thenComparing(IncidentSnapshot::id));
            int start = 0;
            while (start < list.size()) {
                int end = start;
                while (end + 1 < list.size() && !list.get(end + 1).createdAt().isAfter(list.get(start).createdAt().plus(window))) end++;
                int count = end - start + 1;
                if (count >= minimum) {
                    List<IncidentSnapshot> group = list.subList(start, end + 1);
                    result.add(new MajorIncident(group.get(0).id(), IncidentLabel.of(group.get(0).id()), service, service.getLabel(),
                            group.stream().map(IncidentSnapshot::id).toList(), group.stream().map(IncidentSnapshot::label).toList(),
                            group.get(0).createdAt(), group.get(group.size() - 1).createdAt()));
                    start = end + 1;
                } else {
                    start++;
                }
            }
        });
        result.sort(Comparator.comparing(MajorIncident::lastReported).reversed());
        return result;
    }

    public Duration window() { return window; }
}
