package com.bits.itsm.intelligence;

import com.bits.itsm.incident.Incident;
import com.bits.itsm.incident.IncidentStatus;
import com.bits.itsm.support.IncidentSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Intelligent feature E, SLA Risk Detection. Predicts how likely an open incident is to miss its resolution SLA:
 * <pre>
 *   risk = 0.5·elapsed + 0.25·workload + 0.25·history
 *   elapsed  = fraction of the resolution window already used (capped at 1)
 *   workload = assigned technician's load / capacity (0.5 when unassigned: nobody is working on it)
 *   history  = typical resolution time (MTTR) for the category relative to the time that remains (capped at 1)
 * </pre>
 * HIGH &ge; 0.70, MEDIUM &ge; 0.45, otherwise LOW. A breached deadline is always HIGH (score 1).
 */
public class SlaRiskPredictor {
    public enum Level { LOW, MEDIUM, HIGH }

    public record Assessment(Long incidentId, String label, String title, Incident.Priority priority, IncidentStatus status,
                             Long technicianId, Instant resolutionDeadline, double score, Level level, List<String> reasons) { }

    public static final double HIGH = 0.70, MEDIUM = 0.45;

    /**
     * @param workloadRatio technician load ÷ capacity, or {@code null} when unassigned
     * @param categoryMttr  average resolution time of past incidents in the same category, or {@code null} if no history
     */
    public Assessment assess(IncidentSnapshot i, Instant deadline, Instant now, Double workloadRatio, Duration categoryMttr) {
        List<String> reasons = new ArrayList<>();
        long total = Math.max(1, Duration.between(i.createdAt(), deadline).toMillis());
        long used = Duration.between(i.createdAt(), now).toMillis();
        double elapsed = clamp((double) used / total);
        reasons.add(String.format(Locale.ROOT, "%.0f%% of the resolution window used", elapsed * 100));
        double workload = workloadRatio == null ? 0.5 : clamp(workloadRatio);
        reasons.add(workloadRatio == null ? "No technician assigned yet" : String.format(Locale.ROOT, "Technician at %.0f%% of capacity", workload * 100));
        double history = 0.0;
        Duration remaining = Duration.between(now, deadline);
        if (categoryMttr != null) {
            history = remaining.isNegative() || remaining.isZero() ? 1.0 : clamp((double) categoryMttr.toMillis() / remaining.toMillis());
            reasons.add("Typical " + i.category().name().toLowerCase(Locale.ROOT) + " fix takes " + human(categoryMttr) + "; " + human(remaining.isNegative() ? Duration.ZERO : remaining) + " left");
        } else {
            reasons.add("No resolution history for this category");
        }
        double score = now.isAfter(deadline) ? 1.0 : 0.5 * elapsed + 0.25 * workload + 0.25 * history;
        if (now.isAfter(deadline)) reasons.add(0, "Deadline already missed");
        score = Math.round(score * 1000) / 1000.0;
        Level level = score >= HIGH ? Level.HIGH : score >= MEDIUM ? Level.MEDIUM : Level.LOW;
        return new Assessment(i.id(), i.label(), i.title(), i.priority(), i.status(), i.technicianId(), deadline, score, level, List.copyOf(reasons));
    }

    private static double clamp(double v) { return Math.max(0.0, Math.min(1.0, v)); }

    static String human(Duration d) {
        long minutes = d.toMinutes();
        if (minutes < 60) return minutes + " min";
        if (minutes < 48 * 60) return String.format(Locale.ROOT, "%.1f h", minutes / 60.0);
        return String.format(Locale.ROOT, "%.1f days", minutes / 1440.0);
    }
}
