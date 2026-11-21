package com.bits.itsm.incident;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.bits.itsm.user.Student;
import com.bits.itsm.user.Technician;
import com.bits.itsm.user.User;
import com.bits.itsm.user.TechnicianSkill;
import com.bits.itsm.exception.InvalidIncidentException;
import com.bits.itsm.exception.InvalidStateTransitionException;
import com.bits.itsm.incident.state.IncidentLifecycle;
import com.bits.itsm.incident.state.IncidentState;
import jakarta.persistence.*;

/**
 * Aggregate root of the domain model.
 * <ul>
 *   <li><b>Encapsulation</b>: every field is private; there is no public setter for status, priority or
 *       deadlines. State changes happen only through intention-revealing methods that enforce invariants.</li>
 *   <li><b>State pattern</b>: {@link #move(IncidentStatus)} asks the current {@link IncidentState} whether the
 *       edge is legal, so the lifecycle rules live in one class per state.</li>
 *   <li><b>Composition</b>: investigation notes, history and feedback are created by and live only with
 *       their incident (cascade persist, no independent lifecycle).</li>
 *   <li><b>Association</b>: reporter and assigned technician exist independently of the incident.</li>
 * </ul>
 */
@Entity
@Table(name = "incidents")
public class Incident {
    public enum Category { HARDWARE, SOFTWARE, NETWORK, DATABASE, CLOUD, SECURITY, AUTHENTICATION, APPLICATION, STORAGE, OTHER }
    public enum Level { LOW, MEDIUM, HIGH }
    public enum Priority { PENDING, P1, P2, P3, P4 }
    public enum ItService {
        CAMPUS_WIFI("Campus Wi-Fi"), STUDENT_PORTAL("Student Portal"), LIBRARY("Library Services"),
        LAB_COMPUTERS("Lab Computers"), EMAIL("Email Services"), CAMPUS_NETWORK("Campus Network"),
        AUTHENTICATION("Authentication Service"), OTHER("Other");
        private final String label;
        ItService(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 100) private String title;
    @Column(nullable = false, length = 2000) private String description;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Student reporter;
    @Enumerated(EnumType.STRING) private Category category;
    @Enumerated(EnumType.STRING) private ItService affectedService;
    @Enumerated(EnumType.STRING) private Level impact;
    @Enumerated(EnumType.STRING) private Level urgency;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Priority priority = Priority.PENDING;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private IncidentStatus status = IncidentStatus.NEW;
    @ManyToOne(fetch = FetchType.LAZY) private Technician assignedTechnician;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    @Column(nullable = false) private Instant updatedAt = createdAt;
    private Instant acknowledgedAt;
    private Instant resolvedAt;
    private Instant verifiedAt;
    private Instant closedAt;
    @Enumerated(EnumType.STRING) private Level criticality;
    public Level getCriticality() { return criticality; }
    public void captureCriticality(Level criticality) { this.criticality = java.util.Objects.requireNonNull(criticality); }
    private Instant responseDeadline;
    private Instant resolutionDeadline;
    private Integer responseTargetMinutes;
    private Integer resolutionTargetMinutes;
    // Reporter's estimate of how many people are affected (optional; > 100 raises priority one level).
    private Integer affectedUsers;
    // Per-service SLA factor captured with the targets (1.0 = standard policy).
    private Double slaFactor;
    // SLA pause bookkeeping for AWAITING_INFO: when the pause began and the total paused time so far.
    private Instant slaPausedAt;
    @Column(nullable = false) private long slaPausedSeconds;
    @Column(length = 2000) private String resolutionSummary;
    @Column(length = 2000) private String resolutionSteps;
    @OneToMany(mappedBy = "incident", cascade = CascadeType.PERSIST) @OrderBy("createdAt ASC, id ASC")
    private List<InvestigationNote> notes = new ArrayList<>();
    @OneToMany(mappedBy = "incident", cascade = CascadeType.PERSIST) @OrderBy("createdAt ASC, id ASC")
    private List<IncidentHistory> history = new ArrayList<>();
    @OneToOne(mappedBy = "incident", cascade = CascadeType.PERSIST) private IncidentFeedback feedback;
    @OneToMany(mappedBy = "incident", cascade = CascadeType.PERSIST)
    private List<IncidentCollaborator> collaborators = new ArrayList<>();

    @Column(nullable = false) private boolean needsClassificationReview = false;
    @Column(nullable = false) private boolean autoClassified = false;
    @Column(length = 2000) private String classificationNotes;

    protected Incident() { }
    public void editReport(String title, String description) {
        require(IncidentStatus.NEW);
        this.title = field("Title", title, 1, 100);
        this.description = field("Description", description, 20, 2000);
        updatedAt = Instant.now();
    }
    public Incident(String title, String description, Student reporter) {
        this.title = field("Title", title, 1, 100);
        this.description = field("Description", description, 20, 2000);
        this.reporter = java.util.Objects.requireNonNull(reporter);
    }
    public Incident(String title, String description, Student reporter, Category category, ItService service, Level impact, Level urgency) {
        this.title = field("Title", title, 1, 100);
        this.description = field("Description", description, 20, 2000);
        this.reporter = java.util.Objects.requireNonNull(reporter);
        this.category = category;
        this.affectedService = service;
        this.impact = impact;
        this.urgency = urgency;
    }

    // State changes are centralized here; callers cannot set an arbitrary status.
    public void classify(Category category, Priority calculated) {
        requireAny(IncidentStatus.NEW, IncidentStatus.CLASSIFIED);
        if (calculated == null || calculated == Priority.PENDING) throw new IllegalArgumentException("A calculated priority is required.");
        this.category = java.util.Objects.requireNonNull(category);
        priority = calculated;
        this.needsClassificationReview = false;
        enterClassified();
    }
    /** NEW -> CLASSIFIED goes through the State pattern; re-classifying a CLASSIFIED incident is not a status change. */
    private void enterClassified() {
        if (status == IncidentStatus.NEW) move(IncidentStatus.CLASSIFIED); else updatedAt = Instant.now();
    }
    public void adminClassify(Category category, ItService service, Level impact, Level urgency, Level criticality, Priority calculated) {
        requireAny(IncidentStatus.NEW, IncidentStatus.CLASSIFIED);
        if (calculated == null || calculated == Priority.PENDING) throw new IllegalArgumentException("A calculated priority is required.");
        this.category = java.util.Objects.requireNonNull(category, "Category is required");
        this.affectedService = java.util.Objects.requireNonNull(service, "Affected service is required");
        this.impact = java.util.Objects.requireNonNull(impact, "Impact is required");
        this.urgency = java.util.Objects.requireNonNull(urgency, "Urgency is required");
        this.criticality = java.util.Objects.requireNonNull(criticality, "Criticality is required");
        this.priority = calculated;
        this.needsClassificationReview = false;
        this.classificationNotes = null;
        enterClassified();
    }
    public void applyAutoClassification(Category category, ItService service, Level impact, Level urgency, Level criticality, Priority calculated) {
        this.category = category;
        this.affectedService = service;
        this.impact = impact;
        this.urgency = urgency;
        this.criticality = criticality;
        this.priority = calculated;
        this.autoClassified = true;
        this.needsClassificationReview = false;
        enterClassified();
    }
    public void flagForClassificationReview(String reason, Category suggestedCategory, ItService detectedService, Level detectedUrgency, Level detectedImpact) {
        this.needsClassificationReview = true;
        this.classificationNotes = reason;
        this.category = suggestedCategory;
        this.affectedService = detectedService;
        this.urgency = detectedUrgency;
        this.impact = detectedImpact;
        this.priority = Priority.PENDING;
        // Review happens before classification: the incident must still be NEW (no status change here).
        require(IncidentStatus.NEW);
        this.updatedAt = Instant.now();
    }
    // Snapshot targets once; policy edits and transfers never restart clocks.
    public void captureSla(SlaPolicy policy) { captureSla(policy, 1.0); }
    /** Captures targets scaled by the affected service's SLA factor (e.g. 0.5 = twice as fast), at least 1 minute each. */
    public void captureSla(SlaPolicy policy, double serviceFactor) {
        if (responseDeadline != null) return;
        if (priority != policy.getPriority()) throw new IllegalArgumentException("SLA priority mismatch.");
        if (!Double.isFinite(serviceFactor) || serviceFactor < 0.25 || serviceFactor > 4.0) throw new IllegalArgumentException("Service SLA factor must be between 0.25 and 4.");
        slaFactor = serviceFactor;
        responseTargetMinutes = (int) Math.max(1, Math.round(policy.getResponseMinutes() * serviceFactor));
        resolutionTargetMinutes = (int) Math.max(1, Math.round(policy.getResolutionMinutes() * serviceFactor));
        responseDeadline = createdAt.plusSeconds(responseTargetMinutes * 60L);
        resolutionDeadline = createdAt.plusSeconds(resolutionTargetMinutes * 60L);
    }
    public void resetSla(SlaPolicy policy) { resetSla(policy, 1.0); }
    /** Administrator re-classification: recompute targets from the new priority's policy and the service's SLA factor. */
    public void resetSla(SlaPolicy policy, double serviceFactor) {
        if (policy == null) return;
        if (!Double.isFinite(serviceFactor) || serviceFactor < 0.25 || serviceFactor > 4.0) throw new IllegalArgumentException("Service SLA factor must be between 0.25 and 4.");
        slaFactor = serviceFactor;
        responseTargetMinutes = (int) Math.max(1, Math.round(policy.getResponseMinutes() * serviceFactor));
        resolutionTargetMinutes = (int) Math.max(1, Math.round(policy.getResolutionMinutes() * serviceFactor));
        responseDeadline = createdAt.plusSeconds(responseTargetMinutes * 60L);
        resolutionDeadline = createdAt.plusSeconds(resolutionTargetMinutes * 60L);
    }
    public Instant getResponseDeadline() { return responseDeadline; }
    public Instant getResolutionDeadline() { return resolutionDeadline; }
    public SlaClock.Status responseSla(Instant now) { return responseDeadline == null ? null : SlaClock.status(responseDeadline, responseTargetMinutes, acknowledgedAt, now); }
    public SlaClock.Status resolutionSla(Instant now) {
        if (resolutionDeadline == null) return null;
        // While paused the clock is frozen at the moment the information request was sent.
        Instant effectiveNow = slaPausedAt != null && slaPausedAt.isBefore(now) ? slaPausedAt : now;
        return SlaClock.status(resolutionDeadline, resolutionTargetMinutes, resolvedAt, effectiveNow);
    }
    public void assign(Technician technician) {
        require(IncidentStatus.CLASSIFIED);
        if (!technician.isEnabled() || !technician.isAvailable()) throw new com.bits.itsm.exception.TechnicianUnavailableException("Technician is disabled or unavailable.");
        assignedTechnician = technician;
        move(IncidentStatus.ASSIGNED);
    }
    public void acknowledge() { require(IncidentStatus.ASSIGNED); move(IncidentStatus.ACKNOWLEDGED); acknowledgedAt = updatedAt; }
    public boolean isPrimary(Long userId) { return assignedTechnician != null && assignedTechnician.getId().equals(userId); }
    public boolean participates(Long userId) { return isPrimary(userId) || collaborators.stream().anyMatch(c -> c.getTechnician().getId().equals(userId)); }
    public void requireAssignableTeam() {
        if (!state().countsTowardWorkload()) throw new IllegalStateException("Team changes require an assigned incident under investigation.");
    }
    public void reassign(Technician technician) {
        requireAssignableTeam();
        if (participates(technician.getId())) throw new IllegalStateException("Technician already participates in this incident.");
        if (!technician.isEnabled() || !technician.isAvailable()) throw new com.bits.itsm.exception.TechnicianUnavailableException("Technician is disabled or unavailable.");
        assignedTechnician = technician; updatedAt = Instant.now();
        // Reassignment preserves lifecycle progress, timestamps, notes and resolution information.
    }
    public void addCollaborator(Technician technician, String responsibility) {
        requireAssignableTeam();
        if (participates(technician.getId())) throw new IllegalStateException("Technician already participates in this incident.");
        collaborators.add(new IncidentCollaborator(this, technician, responsibility)); updatedAt = Instant.now();
    }
    public TechnicianSkill requiredSkill() {
        if (category == null) return null;
        return category == Category.OTHER ? null : category == Category.APPLICATION ? TechnicianSkill.APPLICATION_SUPPORT : TechnicianSkill.valueOf(category.name());
    }
    public TechnicianSkill preferredSkill() {
        if (requiredSkill() != null) return requiredSkill();
        if (affectedService == null) return null;
        return switch (affectedService) {
            case CAMPUS_WIFI, CAMPUS_NETWORK -> TechnicianSkill.NETWORK;
            case LAB_COMPUTERS -> TechnicianSkill.HARDWARE;
            case AUTHENTICATION -> TechnicianSkill.AUTHENTICATION;
            case STUDENT_PORTAL, LIBRARY, EMAIL -> TechnicianSkill.APPLICATION_SUPPORT;
            case OTHER -> null;
        };
    }
    public void start() { require(IncidentStatus.ACKNOWLEDGED); move(IncidentStatus.IN_PROGRESS); }

    /** IN_PROGRESS → AWAITING_INFO: the technician needs an answer; the resolution SLA clock pauses. */
    public void requestInformation() { requestInformation(Instant.now()); }
    public void requestInformation(Instant now) {
        require(IncidentStatus.IN_PROGRESS);
        move(IncidentStatus.AWAITING_INFO, now);
        slaPausedAt = now;
    }

    /** AWAITING_INFO → IN_PROGRESS: the reporter replied; the deadline is pushed back by the paused time. */
    public void informationReceived() { informationReceived(Instant.now()); }
    public void informationReceived(Instant now) {
        require(IncidentStatus.AWAITING_INFO);
        if (slaPausedAt != null && now.isAfter(slaPausedAt)) {
            java.time.Duration paused = java.time.Duration.between(slaPausedAt, now);
            slaPausedSeconds += paused.getSeconds();
            if (resolutionDeadline != null) resolutionDeadline = resolutionDeadline.plus(paused);
        }
        slaPausedAt = null;
        move(IncidentStatus.IN_PROGRESS, now);
    }
    public void resolve(String summary, String steps) {
        require(IncidentStatus.IN_PROGRESS);
        String validSummary = field("Resolution summary", summary, 1, 2000);
        String validSteps = field("Resolution steps", steps, 1, 2000);
        resolutionSummary = validSummary;
        resolutionSteps = validSteps;
        move(IncidentStatus.RESOLVED); resolvedAt = updatedAt;
    }
    public void verify() { require(IncidentStatus.RESOLVED); move(IncidentStatus.VERIFIED); verifiedAt = updatedAt; }
    public void issueStillExists() {
        require(IncidentStatus.RESOLVED);
        // A rejected resolution returns to the same technician; its contents remain in history.
        move(IncidentStatus.IN_PROGRESS);
        resolutionSummary = null; resolutionSteps = null; resolvedAt = null;
    }
    public void close() { require(IncidentStatus.VERIFIED); move(IncidentStatus.CLOSED); closedAt = updatedAt; }
    public void require(IncidentStatus expected) {
        if (status != expected) throw new InvalidStateTransitionException(status, expected, "This action requires " + expected + "; current status is " + status + ".");
    }
    /** Precondition for actions allowed in several states (e.g. notes while IN_PROGRESS or AWAITING_INFO). */
    public void requireAny(IncidentStatus... allowed) {
        for (IncidentStatus s : allowed) if (status == s) return;
        throw new InvalidStateTransitionException(status, allowed[0], "This action requires " + java.util.Arrays.toString(allowed) + "; current status is " + status + ".");
    }
    /** Behaviour object for the current status (State pattern). */
    public IncidentState state() { return IncidentLifecycle.of(status); }
    private void move(IncidentStatus next) { move(next, Instant.now()); }
    // Single choke point for every status change: the current state validates the edge first.
    private void move(IncidentStatus next, Instant at) { state().requireTransition(next); status = next; updatedAt = at; }
    public void addNote(User author, String content, InvestigationNote.Visibility visibility, InvestigationNote.Kind kind) {
        notes.add(new InvestigationNote(this, author, content, visibility, kind)); updatedAt = Instant.now();
    }
    public void record(User actor, String action, String description, InvestigationNote.Visibility visibility) {
        history.add(new IncidentHistory(this, actor, action, description, visibility));
    }
    public void giveFeedback(Student student, int rating, String comment) {
        if (status != IncidentStatus.VERIFIED && status != IncidentStatus.CLOSED) throw new IllegalStateException("Verify the resolution before submitting feedback.");
        if (feedback != null) throw new IllegalStateException("Feedback has already been submitted.");
        feedback = new IncidentFeedback(this, student, rating, comment); updatedAt = Instant.now();
    }
    /** Validates an incident field; a violation is an "invalid incident" in the brief's terms. */
    private static String field(String name, String value, int min, int max) {
        try {
            return text(value, min, max);
        } catch (IllegalArgumentException e) {
            throw new InvalidIncidentException(name + " must contain " + min + "–" + max + " characters.");
        }
    }
    static String text(String value, int min, int max) {
        if (value == null || value.trim().length() < min || value.trim().length() > max) throw new IllegalArgumentException("Text must contain " + min + "–" + max + " characters.");
        return value.trim();
    }
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Student getReporter() { return reporter; }
    public Category getCategory() { return category; }
    public ItService getAffectedService() { return affectedService; }
    public Level getImpact() { return impact; }
    public Level getUrgency() { return urgency; }
    public Priority getPriority() { return priority; }
    public IncidentStatus getStatus() { return status; }
    public Technician getAssignedTechnician() { return assignedTechnician; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public Instant getClosedAt() { return closedAt; }
    public Instant getSlaPausedAt() { return slaPausedAt; }
    public Integer getAffectedUsers() { return affectedUsers; }
    public Double getSlaFactor() { return slaFactor; }
    public Integer getResponseTargetMinutes() { return responseTargetMinutes; }
    public Integer getResolutionTargetMinutes() { return resolutionTargetMinutes; }
    /** Set by the reporter while the incident is still NEW. */
    public void reportAffectedUsers(Integer count) {
        require(IncidentStatus.NEW);
        if (count != null && (count < 1 || count > 100_000)) throw new InvalidIncidentException("Affected users must be between 1 and 100000.");
        affectedUsers = count;
    }
    public long getSlaPausedSeconds() { return slaPausedSeconds; }
    public boolean isSlaPaused() { return !state().slaClockRunning() && status == IncidentStatus.AWAITING_INFO; }
    public String getResolutionSummary() { return resolutionSummary; }
    public String getResolutionSteps() { return resolutionSteps; }
    public List<InvestigationNote> getNotes() { return List.copyOf(notes); }
    public List<IncidentHistory> getHistory() { return List.copyOf(history); }
    public IncidentFeedback getFeedback() { return feedback; }
    public List<IncidentCollaborator> getCollaborators() { return List.copyOf(collaborators); }
    public boolean isNeedsClassificationReview() { return needsClassificationReview; }
    public void setNeedsClassificationReview(boolean needsClassificationReview) { this.needsClassificationReview = needsClassificationReview; }
    public boolean isAutoClassified() { return autoClassified; }
    public void setAutoClassified(boolean autoClassified) { this.autoClassified = autoClassified; }
    public String getClassificationNotes() { return classificationNotes; }
    public void setClassificationNotes(String classificationNotes) { this.classificationNotes = classificationNotes; }
}
