package com.bits.itsm.priority;

import com.bits.itsm.exception.InvalidIncidentException;
import com.bits.itsm.exception.InvalidStateTransitionException;
import com.bits.itsm.incident.Incident;
import com.bits.itsm.incident.Incident.Priority;
import com.bits.itsm.incident.SlaPolicy;
import com.bits.itsm.user.Student;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Brief: number of affected users as a priority factor, and different SLAs for different services. */
class AffectedUsersAndServiceSlaTest {
    private final Student student = new Student("Student", "student01@hyderabad.bits-pilani.ac.in", "$2-test-only", "TEST");
    private Incident incident() {
        return new Incident("Portal down", "The student portal is down for the whole hostel block.", student, Incident.Category.APPLICATION, Incident.ItService.STUDENT_PORTAL, Incident.Level.MEDIUM, Incident.Level.MEDIUM);
    }

    @ParameterizedTest
    @CsvSource({"P3,150,P2", "P2,101,P1", "P1,5000,P1", "P4,100,P4", "P3,1,P3"})
    void moreThanOneHundredAffectedUsersRaisesPriorityOneLevel(Priority calculated, int users, Priority expected) {
        assertEquals(expected, AffectedUsersRule.apply(calculated, users));
    }

    @Test void unknownUserCountLeavesPriorityUnchanged() {
        assertEquals(Priority.P3, AffectedUsersRule.apply(Priority.P3, null));
        assertThrows(IllegalArgumentException.class, () -> AffectedUsersRule.apply(Priority.PENDING, 500));
    }

    @Test void reporterCanStateAffectedUsersOnlyWhileNewAndWithinBounds() {
        Incident i = incident();
        i.reportAffectedUsers(250);
        assertEquals(250, i.getAffectedUsers());
        assertThrows(InvalidIncidentException.class, () -> i.reportAffectedUsers(0));
        assertThrows(InvalidIncidentException.class, () -> i.reportAffectedUsers(100_001));
        i.classify(Incident.Category.APPLICATION, Priority.P2);
        assertThrows(InvalidStateTransitionException.class, () -> i.reportAffectedUsers(10));
    }

    @Test void serviceFactorScalesBothSlaTargets() {
        Incident i = incident(); i.classify(Incident.Category.APPLICATION, Priority.P2);
        i.captureSla(new SlaPolicy(Priority.P2, 15, 120), 0.5);
        assertEquals(8, i.getResponseTargetMinutes());      // 7.5 rounds to 8
        assertEquals(60, i.getResolutionTargetMinutes());
        assertEquals(Duration.ofMinutes(60), Duration.between(i.getCreatedAt(), i.getResolutionDeadline()));
        assertEquals(0.5, i.getSlaFactor(), 1e-9);
    }

    @Test void defaultFactorKeepsPolicyTargetsAndTinyTargetsStayAtLeastOneMinute() {
        Incident standard = incident(); standard.classify(Incident.Category.APPLICATION, Priority.P1);
        standard.captureSla(new SlaPolicy(Priority.P1, 5, 30));
        assertEquals(5, standard.getResponseTargetMinutes());
        Incident fast = incident(); fast.classify(Incident.Category.APPLICATION, Priority.P1);
        fast.captureSla(new SlaPolicy(Priority.P1, 1, 2), 0.25);
        assertEquals(1, fast.getResponseTargetMinutes());
    }

    @Test void serviceFactorOutsideRangeIsRejected() {
        Incident i = incident(); i.classify(Incident.Category.APPLICATION, Priority.P2);
        assertThrows(IllegalArgumentException.class, () -> i.captureSla(new SlaPolicy(Priority.P2, 15, 120), 0.1));
        assertThrows(IllegalArgumentException.class, () -> i.captureSla(new SlaPolicy(Priority.P2, 15, 120), 5.0));
    }
}
