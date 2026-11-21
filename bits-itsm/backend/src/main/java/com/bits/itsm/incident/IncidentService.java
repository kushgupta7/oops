package com.bits.itsm.incident;

import java.util.List;
import java.util.Locale;
import com.bits.itsm.user.*;
import com.bits.itsm.incident.IncidentData.*;
import com.bits.itsm.assignment.AssignmentSettingsRepository;
import com.bits.itsm.classification.IncidentClassificationService;
import static com.bits.itsm.incident.InvestigationNote.Visibility.*;
import static com.bits.itsm.incident.InvestigationNote.Kind.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class IncidentService {
    private final IncidentRepository incidents;
    private final UserRepository users;
    private final AssignmentSettingsRepository assignmentSettings;
    private final SlaPolicies slaPolicies;
    private final CatalogService catalog;
    private final com.bits.itsm.intelligence.DuplicateDetector duplicates;
    private final com.bits.itsm.priority.PriorityService priorities;
    private final com.bits.itsm.intelligence.CategorySuggester categories;
    private final com.bits.itsm.event.IncidentEventPublisher events;
    private final IncidentClassificationService classificationService;

    public IncidentService(IncidentRepository incidents, UserRepository users,
                           AssignmentSettingsRepository assignmentSettings, SlaPolicies slaPolicies,
                           com.bits.itsm.priority.PriorityService priorities,
                           com.bits.itsm.intelligence.CategorySuggester categories,
                           com.bits.itsm.event.IncidentEventPublisher events,
                           CatalogService catalog,
                           com.bits.itsm.intelligence.DuplicateDetector duplicates,
                           IncidentClassificationService classificationService) {
        this.incidents = incidents; this.users = users; this.assignmentSettings = assignmentSettings; this.slaPolicies = slaPolicies;
        this.priorities = priorities;
        this.categories = categories;
        this.events = events; this.catalog = catalog; this.duplicates = duplicates;
        this.classificationService = classificationService;
    }

    private User actor(String email) {
        return users.findByEmail(email).filter(User::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in with an enabled account."));
    }
    private void role(User user, Role role) {
        if (user.getRole() != role) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This action is not permitted for your role.");
    }
    private void access(Incident incident, User user) {
        boolean allowed = switch (user.getRole()) {
            case ADMIN -> true;
            case STUDENT -> incident.getReporter().getId().equals(user.getId());
            case TECHNICIAN -> incident.participates(user.getId());
        };
        if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this incident.");
    }
    private Incident editable(Long id, User user, Role role) { return editable(id, user, role, false); }
    private Incident editable(Long id, User user, Role role, boolean collaboratorAllowed) {
        role(user, role);
        Incident incident = incidents.findForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found."));
        access(incident, user);
        if (role == Role.TECHNICIAN && !collaboratorAllowed && !incident.isPrimary(user.getId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the primary technician may perform this action.");
        return incident;
    }
    public Options options(String email) {
        actor(email);
        return new Options(catalog.all().stream().filter(c -> c.isEnabled() && c.getKind().equals("CATEGORY")).map(c -> new Option(c.getCode(), c.getLabel())).toList(),
                catalog.all().stream().filter(c -> c.isEnabled() && c.getKind().equals("SERVICE")).map(c -> new Option(c.getCode(), c.getLabel())).toList());
    }
    public List<View> list(String email, boolean mine, String search, IncidentStatus status, Incident.Category category, Incident.Priority priority) {
        return list(email, mine, new SearchCriteria(search, status, category, priority, null, null, null, null, null));
    }
    /**
     * Combinable search over nine criteria: ID/keyword (title, description, INC label), status, category,
     * priority, service, technician, date range (campus time zone) and SLA status. Role scoping is applied
     * first, so a filter can never widen what a user may see.
     */
    public record SearchCriteria(String search, IncidentStatus status, Incident.Category category, Incident.Priority priority,
                                 Incident.ItService service, Long technicianId, String from, String to, SlaClock.Status sla) { }
    public List<View> list(String email, boolean mine, SearchCriteria c) {
        String search = c.search(); IncidentStatus status = c.status(); Incident.Category category = c.category(); Incident.Priority priority = c.priority();
        java.time.ZoneId zone = java.time.ZoneId.of("Asia/Kolkata");
        java.time.Instant from, to;
        try {
            from = c.from() == null || c.from().isBlank() ? null : java.time.LocalDate.parse(c.from()).atStartOfDay(zone).toInstant();
            to = c.to() == null || c.to().isBlank() ? null : java.time.LocalDate.parse(c.to()).plusDays(1).atStartOfDay(zone).toInstant();
        } catch (java.time.format.DateTimeParseException e) {
            throw new com.bits.itsm.exception.ValidationException("Dates must use the format YYYY-MM-DD.");
        }
        if (from != null && to != null && !from.isBefore(to)) throw new com.bits.itsm.exception.ValidationException("The start date must not be after the end date.");
        java.time.Instant now = java.time.Instant.now();
        User user = actor(email);
        if (mine) role(user, Role.STUDENT);
        List<Incident> found = switch (user.getRole()) {
            case STUDENT -> incidents.findByReporterIdOrderByCreatedAtDesc(user.getId());
            case TECHNICIAN -> incidents.findByAssignedTechnicianIdOrderByCreatedAtDesc(user.getId());
            case ADMIN -> incidents.findAllByOrderByCreatedAtDesc();
        };
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT).replaceFirst("^inc-?", "");
        return found.stream().filter(i -> (status == null || i.getStatus() == status) && (category == null || i.getCategory() == category)
                && (priority == null || i.getPriority() == priority)
                && (c.service() == null || i.getAffectedService() == c.service())
                && (c.technicianId() == null || (i.getAssignedTechnician() != null && i.getAssignedTechnician().getId().equals(c.technicianId())))
                && (from == null || !i.getCreatedAt().isBefore(from)) && (to == null || i.getCreatedAt().isBefore(to))
                && (c.sla() == null || worst(i.responseSla(now), i.resolutionSla(now)) == c.sla())
                && (i.getTitle().toLowerCase(Locale.ROOT).contains(query) || i.getId().toString().contains(query)
                    || i.getDescription().toLowerCase(Locale.ROOT).contains(query)))
                .map(i -> view(i, user)).toList();
    }
    /** Overall SLA status = the worse of the response and resolution clocks (null when no SLA yet). */
    static SlaClock.Status worst(SlaClock.Status a, SlaClock.Status b) {
        if (a == null) return b;
        if (b == null) return a;
        if (a == SlaClock.Status.BREACHED || b == SlaClock.Status.BREACHED) return SlaClock.Status.BREACHED;
        if (a == SlaClock.Status.AT_RISK || b == SlaClock.Status.AT_RISK) return SlaClock.Status.AT_RISK;
        if (a == SlaClock.Status.ON_TRACK || b == SlaClock.Status.ON_TRACK) return SlaClock.Status.ON_TRACK;
        return SlaClock.Status.MET;
    }
    public List<View> team(String email) {
        User user = actor(email); role(user, Role.TECHNICIAN);
        return incidents.findTeamIncidents(user.getId()).stream().map(i -> view(i, user)).toList();
    }
    public View get(Long id, String email) {
        User user = actor(email);
        Incident incident = incidents.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found."));
        access(incident, user);
        return view(incident, user);
    }
    // Idempotency keys of recent submissions (key -> time seen). ConcurrentHashMap: requests arrive on parallel threads.
    private final java.util.concurrent.ConcurrentHashMap<String, java.time.Instant> submissions = new java.util.concurrent.ConcurrentHashMap<>();
    static final java.time.Duration SUBMISSION_MEMORY = java.time.Duration.ofMinutes(10);

    @Transactional
    public View create(Create request, String email) { return create(request, email, null); }

    /**
     * Creates an incident. The browser sends a fresh Idempotency-Key per form; if the same key arrives again
     * (double click, network retry) the report is rejected with DuplicateIncidentException instead of creating
     * a second identical incident. Similar-but-new reports are not rejected: they get a duplicate warning.
     */
    @Transactional
    public View create(Create request, String email, String idempotencyKey) {
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : email + ":" + idempotencyKey;
        if (key != null) {
            java.time.Instant now = java.time.Instant.now();
            submissions.entrySet().removeIf(e -> e.getValue().isBefore(now.minus(SUBMISSION_MEMORY)));
            // putIfAbsent is atomic, so two simultaneous clicks cannot both pass.
            if (submissions.putIfAbsent(key, now) != null) {
                throw new com.bits.itsm.exception.DuplicateIncidentException("This report was already submitted. Check My Incidents.");
            }
        }
        try {
            return createOnce(request, email);
        } catch (RuntimeException failed) {
            if (key != null) submissions.remove(key);  // a rejected report may be corrected and sent again with the same key
            throw failed;
        }
    }
    private View createOnce(Create request, String email) {
        User user = actor(email); role(user, Role.STUDENT);
        Incident incident = new Incident(request.title(), request.description(), (Student) user,
                request.category(), request.affectedService(), request.impact(), request.urgency());
        incident.reportAffectedUsers(request.affectedUsers());
        incident.record(user, "CREATED", "Incident reported.", STUDENT_VISIBLE);
        incidents.saveAndFlush(incident);
        events.publish(incident.getId(), user.getId(), "CREATED", null, "NEW", "Incident reported.");

        // Automatic backend classification
        classificationService.processSubmission(incident, user);
        // The incident is already managed: flush (not saveAndFlush, which would merge and copy the new
        // history entry as an empty object) so the cascaded history row is inserted correctly.
        incidents.flush();

        if (incident.isAutoClassified()) {
            events.publish(incident.getId(), user.getId(), "PRIORITY_CHANGED", "PENDING", incident.getPriority().name(),
                    "Automatically classified: " + incident.getPriority());
        }

        duplicates.check(incident.getId());
        return view(incident, user);
    }
    @Transactional
    public View classify(Long id, Classification request, String email) {
        User user = actor(email); Incident incident = editable(id, user, Role.ADMIN);
        if (incident.getAffectedService() == null) {
            throw new IllegalStateException("Affected service is not yet determined. Use adminReclassify to provide service and severity.");
        }
        Incident.Level criticality = priorities.criticality(incident.getAffectedService());
        Incident.Priority calculated = priorities.calculate(incident, criticality);
        Incident.Priority adjusted = com.bits.itsm.priority.AffectedUsersRule.apply(calculated, incident.getAffectedUsers());
        incident.classify(request.category(), adjusted);
        incident.captureCriticality(criticality);
        slaPolicies.capture(incident);
        if (adjusted != calculated) {
            incident.record(user, "PRIORITY_RAISED", "Priority raised from " + calculated + " to " + adjusted + ": " + incident.getAffectedUsers() + " people affected (more than " + com.bits.itsm.priority.AffectedUsersRule.THRESHOLD + ").", STUDENT_VISIBLE);
        }
        if (incident.getSlaFactor() != null && incident.getSlaFactor() != 1.0) {
            incident.record(user, "SLA_ADJUSTED", "Service SLA factor ×" + incident.getSlaFactor() + " for " + incident.getAffectedService().getLabel() + ": response " + incident.getResponseTargetMinutes() + " min, resolution " + incident.getResolutionTargetMinutes() + " min.", STUDENT_VISIBLE);
        }
        events.publish(id, user.getId(), "PRIORITY_CHANGED", "PENDING", incident.getPriority().name(), "Calculated using " + priorities.current().getStrategy() + "; criticality: " + criticality);
        incident.record(user, "PRIORITY_ASSIGNED", "Priority: " + incident.getPriority() + "; criticality: " + criticality, STUDENT_VISIBLE);
        return finish(incident, user, "CLASSIFIED", "Category confirmed: " + request.category());
    }
    @Transactional
    public View adminReclassify(Long id, AdminCorrection request, String email) {
        User user = actor(email); role(user, Role.ADMIN);
        Incident incident = incidents.findForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found."));
        String previousPriority = incident.getPriority() != null ? incident.getPriority().name() : "PENDING";
        classificationService.reclassify(incident, request.affectedService(), request.category(), request.impact(), request.urgency(), request.reason(), user);
        incidents.flush(); // managed entity: flush, never merge (see create)
        events.publish(id, user.getId(), "PRIORITY_CHANGED", previousPriority, incident.getPriority().name(),
                "Administrator updated classification: " + request.reason());
        return view(incident, user);
    }
    @Transactional
    public View edit(Long id, Edit request, String email) {
        User user = actor(email); Incident incident = editable(id, user, Role.STUDENT);
        incident.editReport(request.title(), request.description());
        return finish(incident, user, "REPORT_EDITED", "Reporter updated the report before classification.");
    }
    @Transactional
    public View acknowledge(Long id, String email) {
        User user = actor(email); Incident incident = editable(id, user, Role.TECHNICIAN);
        incident.acknowledge(); return finish(incident, user, "ACKNOWLEDGED", "Technician acknowledged the incident.");
    }
    @Transactional
    public View start(Long id, String email) {
        User user = actor(email); Incident incident = editable(id, user, Role.TECHNICIAN);
        incident.start(); return finish(incident, user, "INVESTIGATION_STARTED", "Investigation started.");
    }
    @Transactional
    public View note(Long id, Note request, String email) {
        User user = actor(email); Incident incident = editable(id, user, Role.TECHNICIAN, true);
        incident.requireAny(IncidentStatus.IN_PROGRESS, IncidentStatus.AWAITING_INFO);
        incident.addNote(user, request.content(), request.visibility(), NOTE);
        incident.record(user, "NOTE_ADDED", "Investigation note added.", request.visibility());
        incidents.flush();
        events.publish(id, user.getId(), request.visibility() == INTERNAL ? "INTERNAL_NOTE_ADDED" : "NOTE_ADDED", "Investigation note added.");
        return view(incident, user);
    }
    @Transactional
    public View information(Long id, Message request, String email, boolean reply) {
        User user = actor(email); Incident incident = editable(id, user, reply ? Role.STUDENT : Role.TECHNICIAN);
        // State pattern: a request moves IN_PROGRESS -> AWAITING_INFO (SLA paused); the reply moves it back.
        if (reply) {
            if (incident.getNotes().stream().noneMatch(n -> n.getKind() == INFORMATION_REQUEST)) throw new IllegalStateException("There is no information request to reply to.");
            incident.informationReceived();
        } else {
            incident.requestInformation();
        }
        incident.addNote(user, request.content(), STUDENT_VISIBLE, reply ? STUDENT_REPLY : INFORMATION_REQUEST);
        return finish(incident, user, reply ? "STUDENT_REPLIED" : "INFORMATION_REQUESTED", reply ? "Student replied to support." : "Technician requested additional information.");
    }
    /**
     * Brief: the employee can "add additional information" at any time while the incident is open.
     * If the technician is waiting for an answer (AWAITING_INFO) the information counts as the reply and resumes the SLA clock.
     */
    @Transactional
    public View addInformation(Long id, Message request, String email) {
        User user = actor(email);
        Incident peek = incidents.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found."));
        if (peek.getStatus() == IncidentStatus.AWAITING_INFO) return information(id, request, email, true);
        Incident incident = editable(id, user, Role.STUDENT);
        incident.requireAny(IncidentStatus.NEW, IncidentStatus.CLASSIFIED, IncidentStatus.ASSIGNED, IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS);
        incident.addNote(user, request.content(), STUDENT_VISIBLE, STUDENT_REPLY);
        return finish(incident, user, "INFORMATION_ADDED", "Reporter added information.");
    }
    @Transactional
    public View resolve(Long id, Resolution request, String email) {
        User user = actor(email); Incident incident = editable(id, user, Role.TECHNICIAN);
        incident.resolve(request.summary(), request.steps());
        return finish(incident, user, "RESOLVED", "Resolution: " + incident.getResolutionSummary() + "\nSteps: " + incident.getResolutionSteps());
    }
    @Transactional
    public View verify(Long id, Verification request, String email) {
        User user = actor(email);
        // Returning to investigation increases active workload: use the same lock order as assignments.
        if (!Boolean.TRUE.equals(request.confirmed())) assignmentSettings.lock();
        Incident incident = editable(id, user, Role.STUDENT);
        if (Boolean.TRUE.equals(request.confirmed())) {
            incident.verify(); return finish(incident, user, "VERIFIED", "Reporter confirmed the issue is resolved.");
        }
        String reason = Incident.text(request.reason(), 1, 2000);
        incident.issueStillExists();
        incident.addNote(user, reason, STUDENT_VISIBLE, STUDENT_REPLY);
        return finish(incident, user, "ISSUE_STILL_EXISTS", "Returned to investigation: " + reason);
    }
    @Transactional
    public View close(Long id, String email) {
        User user = actor(email); Incident incident = editable(id, user, Role.ADMIN);
        incident.close(); return finish(incident, user, "CLOSED", "Administrator closed the verified incident.");
    }
    @Transactional
    public View feedback(Long id, Feedback request, String email) {
        User user = actor(email); Incident incident = editable(id, user, Role.STUDENT);
        incident.giveFeedback((Student) user, request.rating(), request.comment());
        return finish(incident, user, "FEEDBACK_SUBMITTED", "Reporter submitted support feedback.");
    }
    /**
     * Auto-closure policy: an incident left RESOLVED or VERIFIED with no reporter activity since
     * {@code cutoff} is verified/closed by the system. History is attributed to the first enabled
     * administrator because every history row needs an actor; the description makes the automation explicit.
     */
    @Transactional
    public int autoClose(java.time.Instant cutoff) {
        User system = users.findAll().stream().filter(u -> u.getRole() == Role.ADMIN && u.isEnabled()).findFirst().orElse(null);
        if (system == null) return 0;
        int closed = 0;
        for (Incident stale : incidents.findByStatusInAndUpdatedAtBefore(List.of(IncidentStatus.RESOLVED, IncidentStatus.VERIFIED), cutoff)) {
            Incident incident = incidents.findForUpdate(stale.getId()).orElse(null);
            if (incident == null || incident.getUpdatedAt().isAfter(cutoff)) continue;
            if (incident.getStatus() == IncidentStatus.RESOLVED) {
                incident.state().requireTransition(IncidentStatus.VERIFIED, com.bits.itsm.incident.state.LifecycleActor.SYSTEM);
                incident.verify();
                record(incident, system, "AUTO_VERIFIED", "Automatically verified: no reporter response within 72 hours.", "RESOLVED");
            }
            if (incident.getStatus() == IncidentStatus.VERIFIED) {
                incident.state().requireTransition(IncidentStatus.CLOSED, com.bits.itsm.incident.state.LifecycleActor.SYSTEM);
                incident.close();
                record(incident, system, "AUTO_CLOSED", "Automatically closed by the system.", "VERIFIED");
                closed++;
            }
        }
        return closed;
    }
    private void record(Incident incident, User actor, String action, String description, String previous) {
        incident.record(actor, action, description, STUDENT_VISIBLE);
        incidents.flush();
        events.publish(incident.getId(), null, action, previous, incident.getStatus().name(), description);
    }
    private View finish(Incident incident, User actor, String action, String description) {
        incident.record(actor, action, description, STUDENT_VISIBLE);
        incidents.flush();
        String previous = switch (action) {
            case "CLASSIFIED" -> "NEW";
            case "ACKNOWLEDGED" -> "ASSIGNED";
            case "INVESTIGATION_STARTED" -> "ACKNOWLEDGED";
            case "RESOLVED" -> "IN_PROGRESS";
            case "VERIFIED", "ISSUE_STILL_EXISTS" -> "RESOLVED";
            case "CLOSED" -> "VERIFIED";
            case "INFORMATION_REQUESTED" -> "IN_PROGRESS";
            case "STUDENT_REPLIED" -> "AWAITING_INFO";
            default -> incident.getStatus().name();
        };
        // Defence in depth: the state's transition table must also allow this actor (rolls back otherwise).
        IncidentStatus from = IncidentStatus.valueOf(previous);
        if (from != incident.getStatus()) {
            com.bits.itsm.incident.state.IncidentLifecycle.of(from).requireTransition(incident.getStatus(), com.bits.itsm.incident.state.LifecycleActor.of(actor.getRole()));
        }
        events.publish(incident.getId(), actor.getId(), action, previous, incident.getStatus().name(), description);
        return view(incident, actor);
    }
    private static Person person(User user) { return user == null ? null : new Person(user.getId(), user.getName()); }
    private View view(Incident i, User user) {
        boolean student = user.getRole() == Role.STUDENT;
        var notes = i.getNotes().stream().filter(n -> !student || n.getVisibility() == STUDENT_VISIBLE)
                .map(n -> new NoteView(n.getId(), person(n.getAuthor()), n.getContent(), n.getVisibility(), n.getKind(), n.getCreatedAt())).toList();
        var history = i.getHistory().stream().filter(h -> !student || h.getVisibility() == STUDENT_VISIBLE)
                .map(h -> new HistoryView(h.getId(), person(h.getActor()), h.getAction(), h.getDescription(), h.getCreatedAt())).toList();
        var f = i.getFeedback();
        String serviceLabel = i.getAffectedService() == null ? "Awaiting Classification" : i.getAffectedService().getLabel();
        return new View(i.getId(), i.getTitle(), i.getDescription(), person(i.getReporter()), i.getCategory(), i.getAffectedService(),
                serviceLabel, i.getImpact(), i.getUrgency(), i.getPriority(), i.getStatus(), person(i.getAssignedTechnician()),
                i.getCreatedAt(), i.getUpdatedAt(), i.getAcknowledgedAt(), i.getResolvedAt(), i.getVerifiedAt(), i.getClosedAt(),
                i.getResolutionSummary(), i.getResolutionSteps(), notes, history, f == null ? null : new FeedbackView(f.getRating(), f.getComment(), f.getCreatedAt()),
                user.getRole() == Role.TECHNICIAN && i.isPrimary(user.getId()), student ? List.of() : i.getCollaborators().stream()
                    .map(c -> new TeamMember(person(c.getTechnician()), c.getResponsibility())).toList(), categories.suggest(i.getTitle(), i.getDescription()), i.getCriticality(), i.getResponseDeadline(), i.getResolutionDeadline(), i.responseSla(java.time.Instant.now()), i.resolutionSla(java.time.Instant.now()), i.isSlaPaused(), i.getSlaPausedSeconds(), i.getAffectedUsers(), i.getSlaFactor(),
                i.isNeedsClassificationReview(), i.isAutoClassified(), i.getClassificationNotes());
    }
}
