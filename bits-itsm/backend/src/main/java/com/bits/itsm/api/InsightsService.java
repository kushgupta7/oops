package com.bits.itsm.api;

import com.bits.itsm.assignment.WorkloadService;
import com.bits.itsm.exception.UnauthorizedOperationException;
import com.bits.itsm.incident.Incident;
import com.bits.itsm.incident.IncidentRepository;
import com.bits.itsm.incident.IncidentStatus;
import com.bits.itsm.incident.SlaClock;
import com.bits.itsm.incident.state.IncidentLifecycle;
import com.bits.itsm.incident.state.IncidentState;
import com.bits.itsm.intelligence.CorrelationMonitor;
import com.bits.itsm.intelligence.IncidentCorrelator;
import com.bits.itsm.intelligence.ResolutionSuggester;
import com.bits.itsm.intelligence.SlaRiskPredictor;
import com.bits.itsm.notification.sms.SmsChannel;
import com.bits.itsm.sla.SlaCalculator;
import com.bits.itsm.support.IncidentLabel;
import com.bits.itsm.support.IncidentLookup;
import com.bits.itsm.support.IncidentSnapshot;
import com.bits.itsm.support.IncidentStates;
import com.bits.itsm.user.Role;
import com.bits.itsm.user.User;
import com.bits.itsm.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only application service behind the intelligent features, analytics and exports.
 * Business logic stays here; {@link InsightsController} only maps HTTP to these methods.
 */
@Service
@Transactional(readOnly = true)
public class InsightsService {
    static final ZoneId CAMPUS = ZoneId.of("Asia/Kolkata");

    public record StateView(IncidentStatus status, List<IncidentStatus> next, Map<IncidentStatus, List<String>> actors,
                            boolean slaClockRunning, boolean countsTowardWorkload, boolean terminal) { }
    public record DayCount(String day, long reported, long resolved) { }
    public record Analytics(long total, long open, long critical, long resolved, long slaViolations, Double slaCompliancePercent,
                            Double mttaMinutes, Double mttrHours, Double averageFeedback, Map<String, Long> byService,
                            Map<String, Long> byCategory, Map<String, Long> byPriority, Map<String, Long> byStatus,
                            List<DayCount> trend, List<WorkloadService.Workload> workloads) { }

    private final IncidentRepository incidents;
    private final UserRepository users;
    private final IncidentLookup lookup;
    private final SlaCalculator calculator;
    private final WorkloadService workloads;
    private final CorrelationMonitor correlation;
    private final SmsChannel sms;
    private final SlaRiskPredictor risk = new SlaRiskPredictor();
    private final ResolutionSuggester suggester = new ResolutionSuggester();

    public InsightsService(IncidentRepository incidents, UserRepository users, IncidentLookup lookup, SlaCalculator calculator,
                           WorkloadService workloads, CorrelationMonitor correlation, SmsChannel sms) {
        this.incidents = incidents; this.users = users; this.lookup = lookup; this.calculator = calculator;
        this.workloads = workloads; this.correlation = correlation; this.sms = sms;
    }

    private User actor(String email, Role... allowed) {
        User user = users.findByEmail(email).filter(User::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in with an enabled account."));
        if (Arrays.stream(allowed).noneMatch(r -> r == user.getRole())) throw new UnauthorizedOperationException("This view is not available for your role.");
        return user;
    }

    /** The State-pattern transition table, rendered by the UI as the lifecycle diagram. */
    public List<StateView> lifecycle() {
        List<StateView> out = new ArrayList<>();
        for (IncidentState s : IncidentLifecycle.all().values()) {
            Map<IncidentStatus, List<String>> actors = new EnumMap<>(IncidentStatus.class);
            for (IncidentStatus next : s.allowedTransitions()) actors.put(next, s.actorsFor(next).stream().map(Enum::name).toList());
            out.add(new StateView(s.status(), List.copyOf(s.allowedTransitions()), actors, s.slaClockRunning(), s.countsTowardWorkload(), s.isTerminal()));
        }
        return out;
    }

    public List<IncidentCorrelator.MajorIncident> majorIncidents(String email) {
        actor(email, Role.ADMIN);
        return correlation.correlator().correlate(lookup.findAll());
    }

    /** SLA risk for open, prioritised incidents (admin: all; technician: own), highest risk first. */
    public List<SlaRiskPredictor.Assessment> slaRisk(String email) {
        User user = actor(email, Role.ADMIN, Role.TECHNICIAN);
        Instant now = Instant.now();
        List<IncidentSnapshot> all = lookup.findAll();
        Map<Incident.Category, Duration> mttr = categoryMttr(all);
        Map<Long, Double> load = new HashMap<>();
        for (WorkloadService.Workload w : workloads.all()) load.put(w.id(), w.maxWorkload() == 0 ? 1.0 : (double) w.total() / w.maxWorkload());
        return all.stream()
                .filter(i -> i.priority() != Incident.Priority.PENDING && IncidentStates.isOpen(i) && !IncidentStates.isSlaPaused(i))
                .filter(i -> user.getRole() == Role.ADMIN || user.getId().equals(i.technicianId()))
                .map(i -> risk.assess(i, calculator.resolutionDeadline(i), now, i.technicianId() == null ? null : load.get(i.technicianId()), mttr.get(i.category())))
                .sorted(Comparator.comparingDouble(SlaRiskPredictor.Assessment::score).reversed())
                .toList();
    }

    static Map<Incident.Category, Duration> categoryMttr(Collection<IncidentSnapshot> all) {
        Map<Incident.Category, List<Long>> samples = new EnumMap<>(Incident.Category.class);
        for (IncidentSnapshot i : all) {
            if (i.resolvedAt() != null && i.category() != null) samples.computeIfAbsent(i.category(), c -> new ArrayList<>()).add(Duration.between(i.createdAt(), i.resolvedAt()).toMillis());
        }
        Map<Incident.Category, Duration> out = new EnumMap<>(Incident.Category.class);
        samples.forEach((c, list) -> out.put(c, Duration.ofMillis(Math.round(list.stream().mapToLong(Long::longValue).average().orElse(0)))));
        return out;
    }

    /** Intelligent feature G for the technician working the incident (or an admin). */
    public List<ResolutionSuggester.Suggestion> suggestedResolutions(Long id, String email) {
        User user = actor(email, Role.ADMIN, Role.TECHNICIAN);
        Incident incident = incidents.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found."));
        if (user.getRole() == Role.TECHNICIAN && !incident.participates(user.getId())) throw new UnauthorizedOperationException("You do not have access to this incident.");
        List<ResolutionSuggester.PastResolution> history = incidents.findAllByOrderByCreatedAtDesc().stream()
                .filter(i -> !i.getId().equals(id) && i.getResolutionSummary() != null)
                .map(i -> new ResolutionSuggester.PastResolution(i.getId(), i.getTitle(), i.getDescription(), i.getResolutionSummary(), i.getResolutionSteps()))
                .toList();
        return suggester.suggest(incident.getTitle(), incident.getDescription(), history, 3);
    }

    public List<SmsChannel.SentSms> smsOutbox(String email) {
        actor(email, Role.ADMIN);
        return sms.recent();
    }

    public Analytics analytics(String email) {
        actor(email, Role.ADMIN);
        Instant now = Instant.now();
        List<Incident> all = incidents.findAll();
        long open = all.stream().filter(i -> i.getResolvedAt() == null).count();
        long critical = all.stream().filter(i -> i.getPriority() == Incident.Priority.P1 && i.getResolvedAt() == null).count();
        long resolved = all.size() - open;
        long violations = all.stream().filter(i -> i.responseSla(now) == SlaClock.Status.BREACHED || i.resolutionSla(now) == SlaClock.Status.BREACHED).count();
        List<Incident> measured = all.stream().filter(i -> i.getResolutionDeadline() != null && i.getResolvedAt() != null).toList();
        Double compliance = measured.isEmpty() ? null : round(100.0 * measured.stream().filter(i -> i.resolutionSla(now) == SlaClock.Status.MET && i.responseSla(now) != SlaClock.Status.BREACHED).count() / measured.size());
        OptionalDouble mtta = all.stream().filter(i -> i.getAcknowledgedAt() != null).mapToDouble(i -> Duration.between(i.getCreatedAt(), i.getAcknowledgedAt()).toSeconds() / 60.0).average();
        OptionalDouble mttr = all.stream().filter(i -> i.getResolvedAt() != null).mapToDouble(i -> Duration.between(i.getCreatedAt(), i.getResolvedAt()).toSeconds() / 3600.0).average();
        OptionalDouble feedback = all.stream().filter(i -> i.getFeedback() != null).mapToInt(i -> i.getFeedback().getRating()).average();
        LocalDate today = LocalDate.now(CAMPUS);
        // LinkedHashMap keeps the 30 days in calendar order for the chart.
        Map<LocalDate, long[]> days = new LinkedHashMap<>();
        for (int d = 29; d >= 0; d--) days.put(today.minusDays(d), new long[2]);
        for (Incident i : all) {
            long[] c = days.get(LocalDate.ofInstant(i.getCreatedAt(), CAMPUS)); if (c != null) c[0]++;
            if (i.getResolvedAt() != null) { long[] r = days.get(LocalDate.ofInstant(i.getResolvedAt(), CAMPUS)); if (r != null) r[1]++; }
        }
        List<DayCount> trend = days.entrySet().stream().map(e -> new DayCount(e.getKey().toString(), e.getValue()[0], e.getValue()[1])).toList();
        return new Analytics(all.size(), open, critical, resolved, violations, compliance,
                mtta.isPresent() ? round(mtta.getAsDouble()) : null, mttr.isPresent() ? round(mttr.getAsDouble()) : null,
                feedback.isPresent() ? round(feedback.getAsDouble()) : null,
                count(all, i -> i.getAffectedService() == null ? "Awaiting classification" : i.getAffectedService().getLabel()),
                count(all, i -> i.getCategory() == null ? "UNCLASSIFIED" : i.getCategory().name()),
                count(all, i -> i.getPriority().name()), count(all, i -> i.getStatus().name()), trend, workloads.all());
    }

    /** CSV export of every incident (admin). Values are quoted and formula-injection safe. */
    public String csv(String email) {
        actor(email, Role.ADMIN);
        Instant now = Instant.now();
        StringBuilder out = new StringBuilder("id,title,status,priority,category,service,reporter,technician,created_at,acknowledged_at,resolved_at,response_sla,resolution_sla,feedback\n");
        for (Incident i : incidents.findAllByOrderByCreatedAtDesc()) {
            out.append(String.join(",", List.of(IncidentLabel.of(i.getId()), cell(i.getTitle()), i.getStatus().name(), i.getPriority().name(),
                    i.getCategory() == null ? "" : i.getCategory().name(), cell(i.getAffectedService() == null ? "" : i.getAffectedService().getLabel()), cell(i.getReporter().getName()),
                    cell(i.getAssignedTechnician() == null ? "" : i.getAssignedTechnician().getName()), str(i.getCreatedAt()), str(i.getAcknowledgedAt()),
                    str(i.getResolvedAt()), str(i.responseSla(now)), str(i.resolutionSla(now)), i.getFeedback() == null ? "" : String.valueOf(i.getFeedback().getRating())))).append('\n');
        }
        return out.toString();
    }

    static String cell(String value) {
        if (value == null) return "\"\"";
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) v = "'" + v;  // spreadsheet formula injection guard
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }

    private static Map<String, Long> count(List<Incident> all, java.util.function.Function<Incident, String> key) {
        return all.stream().collect(Collectors.groupingBy(key, TreeMap::new, Collectors.counting()));
    }

    private static double round(double v) { return Math.round(v * 100) / 100.0; }
}
