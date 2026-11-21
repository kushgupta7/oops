package com.bits.itsm.api;

import com.bits.itsm.intelligence.IncidentCorrelator;
import com.bits.itsm.intelligence.ResolutionSuggester;
import com.bits.itsm.intelligence.SlaRiskPredictor;
import com.bits.itsm.notification.sms.SmsChannel;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP mapping only: intelligent features, analytics and exports. URL prefixes follow SecurityConfig
 * (/api/admin/** = ADMIN, /api/technician/** = TECHNICIAN); the service re-checks the role.
 */
@RestController
@RequestMapping("/api")
public class InsightsController {
    private final InsightsService service;

    public InsightsController(InsightsService service) { this.service = service; }

    @GetMapping("/incidents/lifecycle") public List<InsightsService.StateView> lifecycle() { return service.lifecycle(); }
    @GetMapping("/admin/insights/major-incidents") public List<IncidentCorrelator.MajorIncident> majors(Authentication auth) { return service.majorIncidents(auth.getName()); }
    @GetMapping({"/admin/insights/sla-risk", "/technician/sla-risk"}) public List<SlaRiskPredictor.Assessment> risk(Authentication auth) { return service.slaRisk(auth.getName()); }
    @GetMapping("/admin/insights/analytics") public InsightsService.Analytics analytics(Authentication auth) { return service.analytics(auth.getName()); }
    @GetMapping("/admin/sms-outbox") public List<SmsChannel.SentSms> sms(Authentication auth) { return service.smsOutbox(auth.getName()); }
    @GetMapping({"/technician/incidents/{id}/suggested-resolutions", "/admin/incidents/{id}/suggested-resolutions"})
    public List<ResolutionSuggester.Suggestion> suggestions(@PathVariable Long id, Authentication auth) { return service.suggestedResolutions(id, auth.getName()); }

    @GetMapping("/admin/reports/incidents.csv")
    public ResponseEntity<String> csv(Authentication auth) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bitsolve-incidents.csv\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(service.csv(auth.getName()));
    }
}
