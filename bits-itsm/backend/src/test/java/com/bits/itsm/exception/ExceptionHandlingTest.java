package com.bits.itsm.exception;

import com.bits.itsm.incident.Incident;
import com.bits.itsm.incident.IncidentStatus;
import com.bits.itsm.user.Student;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The brief's twelve error situations each map to a BitsolveException subtype with a stable code and status. */
class ExceptionHandlingTest {
    private final Student student = new Student("Student", "student01@hyderabad.bits-pilani.ac.in", "$2-test-only", "TEST");

    @Test void everyBriefSituationHasAStableCodeAndHttpStatus() {
        List<BitsolveException> all = List.of(new InvalidLoginException("x"), new UnauthorizedOperationException("x"), new InvalidIncidentException("x"),
                new InvalidStateTransitionException(IncidentStatus.NEW, IncidentStatus.CLOSED), new TechnicianUnavailableException("x"), new WorkloadExceededException("x"),
                new ServiceNotFoundException("x"), new MissingSlaConfigurationException("x"), new DuplicateIncidentException("x"), new PersistenceException("x"),
                new ExternalServiceException("x"), new ValidationException("x"));
        assertEquals(List.of(401, 403, 400, 409, 409, 409, 404, 409, 409, 500, 502, 400), all.stream().map(BitsolveException::httpStatus).toList());
        assertEquals(12, all.stream().map(BitsolveException::code).distinct().count());
    }

    @Test void invalidIncidentFieldsRaiseInvalidIncidentWithTheFieldName() {
        InvalidIncidentException e = assertThrows(InvalidIncidentException.class, () -> new Incident("Wi-Fi", "too short", student, Incident.Category.NETWORK, Incident.ItService.CAMPUS_WIFI, Incident.Level.LOW, Incident.Level.LOW));
        assertTrue(e.getMessage().startsWith("Description"));
        assertEquals(400, e.httpStatus());
    }

    @Test void databaseFailuresAreWrappedWithoutLeakingDetails() {
        var request = new jakarta.servlet.http.HttpServletRequest() {
            public String getRequestURI() { return "/api/incidents"; }
            public String getRemoteAddr() { return "127.0.0.1"; }
            public String getHeader(String n) { return null; }
        };
        var response = new GlobalApiErrors().database(new org.springframework.dao.DataAccessException("duplicate key value violates constraint users_email") { }, request);
        assertEquals(500, response.getStatusCode().value());
        assertEquals("PERSISTENCE_FAILURE", response.getBody().code());
        assertFalse(response.getBody().message().contains("constraint"));
        assertEquals("/api/incidents", response.getBody().path());
    }

    @Test void constructorsRejectMissingCodes() {
        assertThrows(IllegalArgumentException.class, () -> new BitsolveException("", 400, "m") { });
        assertThrows(IllegalArgumentException.class, () -> new BitsolveException("C", 200, "m") { });
    }
}
