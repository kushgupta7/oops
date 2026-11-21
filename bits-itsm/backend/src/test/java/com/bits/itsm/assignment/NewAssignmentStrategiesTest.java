package com.bits.itsm.assignment;

import com.bits.itsm.assignment.AssignmentStrategy.Candidate;
import com.bits.itsm.incident.Incident;
import com.bits.itsm.user.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

/** Round-robin, priority-based, smart recommendation and the audited fallback chain. */
class NewAssignmentStrategiesTest {
    private final Student student = new Student("Student", "student01@hyderabad.bits-pilani.ac.in", "$2-test-only", "TEST");

    Technician tech(long id, TechnicianSkill skill) {
        var t = new Technician("Tech " + id, "tech" + id + "@hyderabad.bits-pilani.ac.in", "$2-test-only", skill.name());
        ReflectionTestUtils.setField(t, "id", id); return t;
    }
    Incident incident(Incident.Category category, Incident.Priority priority) {
        Incident i = new Incident("Network issue", "The campus network is not responding in hostel.", student, category, Incident.ItService.CAMPUS_WIFI, Incident.Level.HIGH, Incident.Level.MEDIUM);
        i.classify(category, priority); return i;
    }

    @Test void roundRobinRotatesThroughEligibleTechniciansInIdOrder() {
        var a = tech(1, TechnicianSkill.NETWORK); var b = tech(2, TechnicianSkill.NETWORK); var c = tech(3, TechnicianSkill.SOFTWARE);
        var rr = new RoundRobinAssignment();
        var candidates = List.of(new Candidate(b, 0), new Candidate(a, 4), new Candidate(c, 0));
        List<Long> picks = new ArrayList<>();
        for (int n = 0; n < 4; n++) picks.add(rr.assign(incident(Incident.Category.NETWORK, Incident.Priority.P3), candidates).orElseThrow().getId());
        assertEquals(List.of(1L, 2L, 1L, 2L), picks); // software tech never picked for a network incident; load ignored
    }

    @Test void roundRobinReturnsEmptyWhenNobodyIsEligible() {
        assertTrue(new RoundRobinAssignment().assign(incident(Incident.Category.SECURITY, Incident.Priority.P3), List.of(new Candidate(tech(1, TechnicianSkill.NETWORK), 0))).isEmpty());
    }

    @Test void priorityBasedSendsP1ToTheMostExperiencedSeniorTechnician() {
        var junior = tech(1, TechnicianSkill.NETWORK); var senior = tech(2, TechnicianSkill.NETWORK); var veteran = tech(3, TechnicianSkill.NETWORK);
        var candidates = List.of(new Candidate(junior, 0, 0, 1, 0), new Candidate(senior, 2, 2, 5, 0), new Candidate(veteran, 3, 3, 9, 0));
        assertSame(veteran, new PriorityBasedAssignment().assign(incident(Incident.Category.NETWORK, Incident.Priority.P1), candidates).orElseThrow());
    }

    @Test void priorityBasedUsesLeastLoadedForRoutineWork() {
        var busy = tech(1, TechnicianSkill.NETWORK); var free = tech(2, TechnicianSkill.NETWORK);
        var candidates = List.of(new Candidate(busy, 3, 3, 20, 0), new Candidate(free, 0, 0, 0, 0));
        assertSame(free, new PriorityBasedAssignment().assign(incident(Incident.Category.NETWORK, Incident.Priority.P4), candidates).orElseThrow());
    }

    @Test void priorityBasedFindsNobodyWhenNoSeniorIsFreeAndTheRegistryFallsBack() {
        var junior = tech(1, TechnicianSkill.NETWORK);
        var candidates = List.of(new Candidate(junior, 0, 0, 0, 0));
        Incident p1 = incident(Incident.Category.NETWORK, Incident.Priority.P1);
        assertTrue(new PriorityBasedAssignment().assign(p1, candidates).isEmpty());
        var outcome = new AssignmentStrategies().assignWithFallback(AssignmentSettings.Mode.PRIORITY_BASED, p1, candidates).orElseThrow();
        assertSame(junior, outcome.technician());
        assertTrue(outcome.fallback());
        assertNotEquals(AssignmentSettings.Mode.PRIORITY_BASED, outcome.used());
    }

    @Test void fallbackIsNotReportedWhenTheSelectedStrategySucceeds() {
        var t = tech(1, TechnicianSkill.NETWORK);
        var outcome = new AssignmentStrategies().assignWithFallback(AssignmentSettings.Mode.LOAD_BASED, incident(Incident.Category.NETWORK, Incident.Priority.P3), List.of(new Candidate(t, 0))).orElseThrow();
        assertFalse(outcome.fallback()); assertEquals(AssignmentSettings.Mode.LOAD_BASED, outcome.used());
    }

    @Test void noStrategyCanBypassMandatorySkillOrCapacity() {
        var full = tech(1, TechnicianSkill.SECURITY);
        var wrong = tech(2, TechnicianSkill.NETWORK);
        var candidates = List.of(new Candidate(full, 5), new Candidate(wrong, 0));
        assertTrue(new AssignmentStrategies().assignWithFallback(AssignmentSettings.Mode.SMART, incident(Incident.Category.SECURITY, Incident.Priority.P1), candidates).isEmpty());
    }

    @Test void everyModeHasAStrategy() {
        var registry = new AssignmentStrategies();
        for (AssignmentSettings.Mode m : AssignmentSettings.Mode.values()) assertNotNull(registry.forMode(m));
    }

    @Test void smartRecommendationRanksBySkillLoadExperienceAndServiceHistoryWithReasons() {
        var specialist = tech(1, TechnicianSkill.NETWORK); var generalist = tech(2, TechnicianSkill.SOFTWARE); var overloaded = tech(3, TechnicianSkill.NETWORK);
        Incident other = incident(Incident.Category.OTHER, Incident.Priority.P3); // no mandatory skill, Wi-Fi prefers NETWORK
        var ranked = new SmartRecommendation().rank(other, List.of(
                new Candidate(specialist, 1, 1, 6, 4), new Candidate(generalist, 0, 0, 2, 0), new Candidate(overloaded, 4, 8, 6, 4)));
        assertEquals(List.of(1L, 3L, 2L), ranked.stream().map(SmartRecommendation.Recommendation::technicianId).toList());
        assertTrue(ranked.get(0).reasons().stream().anyMatch(r -> r.contains("network")));
        assertTrue(ranked.get(0).reasons().stream().anyMatch(r -> r.contains("Campus Wi-Fi")));
        assertTrue(ranked.get(0).score() > ranked.get(1).score());
        assertEquals(2, new SmartRecommendation().top(other, List.of(new Candidate(specialist, 1), new Candidate(generalist, 0)), 3).size());
    }

    @Test void smartScoreStaysWithinZeroAndOne() {
        var t = tech(1, TechnicianSkill.NETWORK);
        var r = new SmartRecommendation().rank(incident(Incident.Category.NETWORK, Incident.Priority.P2), List.of(new Candidate(t, 0, 0, 10, 10))).get(0);
        assertEquals(1.0, r.score(), 1e-9);
    }
}
