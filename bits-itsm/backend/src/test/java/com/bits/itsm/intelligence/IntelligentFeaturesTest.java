package com.bits.itsm.intelligence;

import com.bits.itsm.incident.Incident;
import com.bits.itsm.incident.IncidentStatus;
import com.bits.itsm.support.IncidentSnapshot;
import com.bits.itsm.testsupport.TestData.Snap;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.bits.itsm.testsupport.TestData.T0;
import static org.junit.jupiter.api.Assertions.*;

/** Correlation (B), SLA risk (E), TF-IDF similarity and suggested resolutions (G). */
class IntelligentFeaturesTest {
    private IncidentSnapshot wifi(long id, int minute) {
        return Snap.of(id, Incident.Priority.P2, IncidentStatus.ASSIGNED, T0.plus(Duration.ofMinutes(minute))).service(Incident.ItService.CAMPUS_WIFI).build();
    }

    @Test void threeReportsOnOneServiceWithinThirtyMinutesFormAMajorIncident() {
        var majors = new IncidentCorrelator().correlate(List.of(wifi(1, 0), wifi(2, 10), wifi(3, 30), wifi(4, 90)));
        assertEquals(1, majors.size());
        assertEquals(List.of(1L, 2L, 3L), majors.get(0).incidentIds());
        assertEquals(1L, majors.get(0).anchorIncidentId());
        assertEquals("Campus Wi-Fi", majors.get(0).serviceLabel());
    }

    @Test void windowBoundaryIsInclusiveButOneMinuteLaterIsNot() {
        assertEquals(1, new IncidentCorrelator().correlate(List.of(wifi(1, 0), wifi(2, 15), wifi(3, 30))).size());
        assertEquals(0, new IncidentCorrelator().correlate(List.of(wifi(1, 0), wifi(2, 15), wifi(3, 31))).size());
    }

    @Test void differentServicesResolvedIncidentsAndOtherAreNotCorrelated() {
        IncidentSnapshot portal = Snap.of(5, Incident.Priority.P2, IncidentStatus.NEW, T0).service(Incident.ItService.STUDENT_PORTAL).build();
        IncidentSnapshot resolved = Snap.of(6, Incident.Priority.P2, IncidentStatus.RESOLVED, T0.plusSeconds(60)).service(Incident.ItService.CAMPUS_WIFI).resolved(T0.plusSeconds(120)).build();
        assertTrue(new IncidentCorrelator().correlate(List.of(wifi(1, 0), wifi(2, 5), portal, resolved)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new IncidentCorrelator(Duration.ZERO, 3));
    }

    @Test void slaRiskRisesWithElapsedTimeWorkloadAndHistory() {
        var predictor = new SlaRiskPredictor();
        IncidentSnapshot i = Snap.of(1, Incident.Priority.P2, IncidentStatus.IN_PROGRESS, T0).tech(9L).build();
        Instant deadline = T0.plus(Duration.ofHours(2));
        var early = predictor.assess(i, deadline, T0.plus(Duration.ofMinutes(6)), 0.2, Duration.ofMinutes(30));
        var late = predictor.assess(i, deadline, T0.plus(Duration.ofMinutes(100)), 1.0, Duration.ofHours(3));
        assertEquals(SlaRiskPredictor.Level.LOW, early.level());
        assertEquals(SlaRiskPredictor.Level.HIGH, late.level());
        assertTrue(late.score() > early.score());
        assertFalse(late.reasons().isEmpty());
    }

    @Test void missedDeadlineIsAlwaysHighRiskAndUnassignedCountsAsPressure() {
        var predictor = new SlaRiskPredictor();
        IncidentSnapshot i = Snap.of(1, Incident.Priority.P1, IncidentStatus.CLASSIFIED, T0).build();
        var missed = predictor.assess(i, T0.plusSeconds(60), T0.plusSeconds(61), null, null);
        assertEquals(1.0, missed.score(), 1e-9);
        assertEquals("Deadline already missed", missed.reasons().get(0));
        var fresh = predictor.assess(i, T0.plus(Duration.ofHours(8)), T0, null, null);
        assertEquals(0.125, fresh.score(), 1e-9); // 0.5·0 + 0.25·0.5 (unassigned) + 0.25·0
    }

    @Test void tfIdfGivesRareSharedWordsMoreWeightThanCommonOnes() {
        var model = new TfIdfSimilarityModel().fit(List.of("projector not working", "wifi not working", "printer not working", "vpn not working"));
        double rareMatch = model.similarity("projector flickering in lecture hall", "projector not working");
        double commonMatch = model.similarity("keyboard not working", "printer not working");
        assertTrue(rareMatch > 0);
        assertEquals(1.0, model.similarity("vpn not working", "vpn not working"), 1e-9);
        assertEquals(0.0, model.similarity("", "vpn"), 1e-9);
        assertTrue(model.similarity("projector", "projector screen") > model.similarity("projector", "printer"));
        assertTrue(commonMatch >= 0 && commonMatch < 1);
    }

    @Test void suggestedResolutionsReturnTheMostSimilarFixesFirstAndAtMostThree() {
        var history = List.of(
                new ResolutionSuggester.PastResolution(1L, "Wi-Fi drops in hostel", "Wireless keeps disconnecting in Ram Bhawan", "Replaced access point", "Swapped AP-12"),
                new ResolutionSuggester.PastResolution(2L, "Printer jam", "Lab printer jams on every page", "Cleared rollers", "Opened tray"),
                new ResolutionSuggester.PastResolution(3L, "Wi-Fi slow in library", "Wireless very slow on second floor", "Moved channel", "Changed to channel 11"),
                new ResolutionSuggester.PastResolution(4L, "Portal login fails", "Cannot sign in to ERP portal", "Reset SSO session", "Cleared cache"),
                new ResolutionSuggester.PastResolution(5L, "Wi-Fi outage", "Wireless down across hostel block", "Restarted controller", "Rebooted WLC"));
        var suggestions = new ResolutionSuggester().suggest("Hostel Wi-Fi disconnecting", "Wireless keeps dropping in hostel rooms", history, 3);
        assertFalse(suggestions.isEmpty());
        assertTrue(suggestions.size() <= 3);
        assertEquals(1L, suggestions.get(0).incidentId());
        assertTrue(suggestions.stream().noneMatch(s -> s.incidentId() == 2L));
        assertTrue(new ResolutionSuggester().suggest("x", "y", List.of(), 3).isEmpty());
    }

    @Test void jaccardModelMatchesTheExistingDuplicateSimilarity() {
        assertEquals(TextSimilarity.similarity("wifi down", "wi-fi outage"), SimilarityModel.jaccard().similarity("wifi down", "wi-fi outage"), 1e-12);
    }
}
