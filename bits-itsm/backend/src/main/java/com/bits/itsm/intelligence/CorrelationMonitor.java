package com.bits.itsm.intelligence;

import com.bits.itsm.event.EventType;
import com.bits.itsm.event.IncidentEvent;
import com.bits.itsm.sla.ScheduledEvents;
import com.bits.itsm.support.IncidentLookup;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the {@link IncidentCorrelator} in the background and alerts administrators once per major incident
 * (deduplicated in the scheduled_events table, so restarts do not repeat the alert).
 */
@Component
public class CorrelationMonitor {
    private static final Logger log = LoggerFactory.getLogger(CorrelationMonitor.class);
    private final IncidentLookup lookup;
    private final ScheduledEvents events;
    private final IncidentCorrelator correlator = new IncidentCorrelator();

    public CorrelationMonitor(IncidentLookup lookup, ScheduledEvents events) {
        this.lookup = lookup;
        this.events = events;
    }

    @Scheduled(fixedDelayString = "${itsm.correlation.check-interval-ms:30000}")
    public void run() {
        try {
            check(Instant.now());
        } catch (RuntimeException ex) {
            log.error("Correlation pass failed", ex);
        }
    }

    public List<IncidentCorrelator.MajorIncident> check(Instant now) {
        List<IncidentCorrelator.MajorIncident> majors = correlator.correlate(lookup.findAll());
        for (IncidentCorrelator.MajorIncident m : majors) {
            String description = "Major incident on " + m.serviceLabel() + ": " + m.size() + " related reports within "
                    + correlator.window().toMinutes() + " minutes (" + String.join(", ", m.labels()) + ").";
            events.publishOnce(m.anchorIncidentId() + ":MAJOR_INCIDENT:" + m.size(),
                    IncidentEvent.system(EventType.MAJOR_INCIDENT_DETECTED, m.anchorIncidentId(), null, String.valueOf(m.size()), description, now));
        }
        return majors;
    }

    public IncidentCorrelator correlator() { return correlator; }
}
