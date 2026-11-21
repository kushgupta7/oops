package com.bits.itsm.state;

import com.bits.itsm.exception.BitsolveException;
import com.bits.itsm.exception.InvalidStateTransitionException;
import com.bits.itsm.exception.UnauthorizedOperationException;
import com.bits.itsm.incident.*;
import com.bits.itsm.incident.state.IncidentLifecycle;
import com.bits.itsm.incident.state.IncidentState;
import com.bits.itsm.incident.state.LifecycleActor;
import com.bits.itsm.user.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static com.bits.itsm.incident.IncidentStatus.*;
import static org.junit.jupiter.api.Assertions.*;

/** State pattern: every legal edge, every illegal edge, actor rules and the paused SLA clock. */
class IncidentStateMachineTest {
    /** The complete set of legal edges from the design (docs/DESIGN_PATTERNS.md). */
    static final Map<IncidentStatus, Set<IncidentStatus>> LEGAL = Map.of(
            NEW, Set.of(CLASSIFIED), CLASSIFIED, Set.of(ASSIGNED), ASSIGNED, Set.of(ACKNOWLEDGED),
            ACKNOWLEDGED, Set.of(IN_PROGRESS), IN_PROGRESS, Set.of(AWAITING_INFO, RESOLVED),
            AWAITING_INFO, Set.of(IN_PROGRESS), RESOLVED, Set.of(VERIFIED, IN_PROGRESS), VERIFIED, Set.of(CLOSED),
            CLOSED, Set.of());

    private final Student student = new Student("Student", "student01@hyderabad.bits-pilani.ac.in", "$2-test-only", "TEST");
    private final Technician technician = new Technician("Tech", "technician01@hyderabad.bits-pilani.ac.in", "$2-test-only", "NETWORK");

    private Incident inProgress() {
        Incident i = new Incident("Wi-Fi down", "Campus Wi-Fi is down in the library reading room.", student, Incident.Category.NETWORK, Incident.ItService.CAMPUS_WIFI, Incident.Level.HIGH, Incident.Level.HIGH);
        i.classify(Incident.Category.NETWORK, Incident.Priority.P1); i.assign(technician); i.acknowledge(); i.start();
        return i;
    }

    @Test void everyStatusHasExactlyOneStateObjectWithTheDocumentedEdges() {
        for (IncidentStatus s : IncidentStatus.values()) {
            IncidentState state = IncidentLifecycle.of(s);
            assertEquals(s, state.status());
            assertEquals(LEGAL.get(s), state.allowedTransitions(), "edges of " + s);
        }
        assertTrue(IncidentLifecycle.of(CLOSED).isTerminal());
        assertFalse(IncidentLifecycle.of(NEW).isTerminal());
    }

    @Test void everyEdgeNotInTheTableIsRejectedWithInvalidStateTransition() {
        int rejected = 0;
        for (IncidentStatus from : IncidentStatus.values()) for (IncidentStatus to : IncidentStatus.values()) {
            if (LEGAL.get(from).contains(to)) { IncidentLifecycle.of(from).requireTransition(to); continue; }
            InvalidStateTransitionException e = assertThrows(InvalidStateTransitionException.class, () -> IncidentLifecycle.of(from).requireTransition(to));
            assertEquals(from, e.from()); assertEquals(to, e.to()); assertEquals("INVALID_STATE_TRANSITION", e.code()); assertEquals(409, e.httpStatus());
            rejected++;
        }
        assertEquals(81 - 10, rejected); // 9 states squared minus the 10 legal edges
    }

    @Test void newToClosedIsRejectedOnTheEntity() {
        Incident i = new Incident("Printer", "The lab printer is jammed again today.", student, Incident.Category.HARDWARE, Incident.ItService.LAB_COMPUTERS, Incident.Level.LOW, Incident.Level.LOW);
        assertThrows(InvalidStateTransitionException.class, i::close);
        assertEquals(NEW, i.getStatus());
    }

    @ParameterizedTest
    @CsvSource({"NEW,CLASSIFIED,ADMIN,true", "NEW,CLASSIFIED,STUDENT,false", "ASSIGNED,ACKNOWLEDGED,TECHNICIAN,true", "ASSIGNED,ACKNOWLEDGED,ADMIN,false",
            "IN_PROGRESS,AWAITING_INFO,TECHNICIAN,true", "AWAITING_INFO,IN_PROGRESS,STUDENT,true", "AWAITING_INFO,IN_PROGRESS,TECHNICIAN,false",
            "RESOLVED,VERIFIED,STUDENT,true", "RESOLVED,VERIFIED,SYSTEM,true", "RESOLVED,VERIFIED,TECHNICIAN,false", "VERIFIED,CLOSED,ADMIN,true", "VERIFIED,CLOSED,STUDENT,false"})
    void eachStateDecidesWhichActorsMayMoveIt(IncidentStatus from, IncidentStatus to, LifecycleActor actor, boolean allowed) {
        if (allowed) IncidentLifecycle.of(from).requireTransition(to, actor);
        else assertThrows(UnauthorizedOperationException.class, () -> IncidentLifecycle.of(from).requireTransition(to, actor));
    }

    @Test void roleMappingCoversEveryRoleAndSystem() {
        assertEquals(LifecycleActor.ADMIN, LifecycleActor.of(Role.ADMIN));
        assertEquals(LifecycleActor.STUDENT, LifecycleActor.of(Role.STUDENT));
        assertEquals(LifecycleActor.TECHNICIAN, LifecycleActor.of(Role.TECHNICIAN));
        assertEquals(LifecycleActor.SYSTEM, LifecycleActor.of(null));
    }

    @Test void awaitingInfoPausesTheResolutionClockAndExtendsTheDeadlineByThePause() {
        Incident i = inProgress();
        i.captureSla(new SlaPolicy(Incident.Priority.P1, 5, 30));
        Instant before = i.getResolutionDeadline();
        Instant asked = Instant.now();
        i.requestInformation(asked);
        assertEquals(AWAITING_INFO, i.getStatus());
        assertTrue(i.isSlaPaused());
        assertFalse(i.state().slaClockRunning());
        i.informationReceived(asked.plusSeconds(600));
        assertEquals(IN_PROGRESS, i.getStatus());
        assertFalse(i.isSlaPaused());
        assertEquals(before.plusSeconds(600), i.getResolutionDeadline());
        assertEquals(600, i.getSlaPausedSeconds());
    }

    @Test void pausedClockIsFrozenSoTheStatusDoesNotDegradeWhileWaiting() {
        Incident i = inProgress();
        i.captureSla(new SlaPolicy(Incident.Priority.P1, 5, 30));
        Instant asked = i.getResolutionDeadline().minusSeconds(20 * 60); // 10 of 30 minutes used
        i.requestInformation(asked);
        // Long after the original deadline the paused incident still reports the status it had when paused.
        assertEquals(SlaClock.Status.ON_TRACK, i.resolutionSla(i.getResolutionDeadline().plusSeconds(3600)));
    }

    @Test void zeroLengthPauseLeavesTheDeadlineUnchanged() {
        Incident i = inProgress();
        i.captureSla(new SlaPolicy(Incident.Priority.P1, 5, 30));
        Instant before = i.getResolutionDeadline(); Instant t = Instant.now();
        i.requestInformation(t); i.informationReceived(t);
        assertEquals(before, i.getResolutionDeadline());
        assertEquals(0, i.getSlaPausedSeconds());
    }

    @Test void cannotResolveOrRequestTwiceWhileAwaitingInfo() {
        Incident i = inProgress(); i.requestInformation();
        assertThrows(InvalidStateTransitionException.class, () -> i.resolve("Fixed", "Steps"));
        assertThrows(InvalidStateTransitionException.class, i::requestInformation);
        assertThrows(InvalidStateTransitionException.class, () -> inProgress().informationReceived());
    }

    @Test void awaitingInfoStillCountsTowardWorkloadAndAllowsTeamChanges() {
        assertTrue(IncidentLifecycle.of(AWAITING_INFO).countsTowardWorkload());
        assertTrue(com.bits.itsm.assignment.WorkloadService.active(AWAITING_INFO));
        assertFalse(com.bits.itsm.assignment.WorkloadService.active(RESOLVED));
        Incident i = inProgress(); i.requestInformation();
        i.requireAssignableTeam();
    }

    @Test void customExceptionsShareTheBitsolveRoot() {
        BitsolveException e = new InvalidStateTransitionException(NEW, CLOSED);
        assertTrue(e instanceof RuntimeException);
        assertTrue(e.getMessage().contains("NEW") && e.getMessage().contains("CLOSED"));
    }

    @Test void overallSlaStatusIsTheWorseOfBothClocks() {
        assertEquals(SlaClock.Status.BREACHED, IncidentServiceAccess.worst(SlaClock.Status.MET, SlaClock.Status.BREACHED));
        assertEquals(SlaClock.Status.AT_RISK, IncidentServiceAccess.worst(SlaClock.Status.AT_RISK, SlaClock.Status.ON_TRACK));
        assertEquals(SlaClock.Status.MET, IncidentServiceAccess.worst(SlaClock.Status.MET, SlaClock.Status.MET));
        assertNull(IncidentServiceAccess.worst(null, null));
    }
}
