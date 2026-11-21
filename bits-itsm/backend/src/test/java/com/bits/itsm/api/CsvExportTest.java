package com.bits.itsm.api;

import com.bits.itsm.incident.Incident;
import com.bits.itsm.incident.IncidentStatus;
import com.bits.itsm.testsupport.TestData.Snap;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.bits.itsm.testsupport.TestData.T0;
import static org.junit.jupiter.api.Assertions.*;

class CsvExportTest {
    @Test void cellsAreQuotedEscapedAndFormulaSafe() {
        assertEquals("\"Wi-Fi \"\"down\"\"\"", InsightsService.cell("Wi-Fi \"down\""));
        assertEquals("\"'=HYPERLINK(1)\"", InsightsService.cell("=HYPERLINK(1)"));
        assertEquals("\"\"", InsightsService.cell(null));
    }

    @Test void categoryMttrAveragesOnlyResolvedIncidents() {
        var a = Snap.of(1, Incident.Priority.P2, IncidentStatus.RESOLVED, T0).resolved(T0.plus(Duration.ofHours(1))).build();
        var b = Snap.of(2, Incident.Priority.P2, IncidentStatus.RESOLVED, T0).resolved(T0.plus(Duration.ofHours(3))).build();
        var open = Snap.of(3, Incident.Priority.P2, IncidentStatus.IN_PROGRESS, T0).build();
        assertEquals(Duration.ofHours(2), InsightsService.categoryMttr(List.of(a, b, open)).get(Incident.Category.NETWORK));
    }
}
