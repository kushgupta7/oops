package com.bits.itsm.sla;

import com.bits.itsm.incident.IncidentService;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background job for the lifecycle rule "CLOSED automatically after 72 h without reporter response".
 * The idle period honours {@code itsm.sla.time-scale}, so demo mode (e.g. 1 minute = 1 hour) also
 * shrinks it and auto-closure can be shown live.
 */
@Component
public class AutoCloseScheduler {
    private static final Logger log = LoggerFactory.getLogger(AutoCloseScheduler.class);
    private final IncidentService incidents;
    private final Duration idle;

    public AutoCloseScheduler(IncidentService incidents, SlaCalculator calculator,
                              @Value("${itsm.autoclose.idle-hours:72}") double idleHours) {
        if (!Double.isFinite(idleHours) || idleHours <= 0) throw new IllegalArgumentException("Auto-close idle period must be positive.");
        this.incidents = incidents;
        this.idle = calculator.scaled(Duration.ofMillis(Math.round(idleHours * 3_600_000)));
    }

    @Scheduled(fixedDelayString = "${itsm.autoclose.check-interval-ms:60000}")
    public void run() {
        try {
            int closed = incidents.autoClose(Instant.now().minus(idle));
            if (closed > 0) log.info("Auto-closed {} incident(s)", closed);
        } catch (RuntimeException ex) {
            log.error("Auto-close pass failed", ex);
        }
    }

    public Duration idlePeriod() { return idle; }
}
