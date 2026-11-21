package com.bits.itsm.user;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Development-only historical data so dashboards, duplicate detection, SLA risk and suggested resolutions
 * have something to work with in the demo. Same guards as {@link DemoUserSeeder} (dev profile + explicit
 * opt-in) and it only ever writes to an <b>empty</b> incidents table, so it cannot touch real records.
 * Data is inserted with SQL because historical timestamps cannot be produced through the entity API
 * (which deliberately has no setters for createdAt/resolvedAt).
 */
@Component
@Profile("dev & !prod & !production")
@ConditionalOnProperty(name = "bitsolve.demo.seed-enabled", havingValue = "true")
public class DemoIncidentSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoIncidentSeeder.class);

    /** title, description, category, service, impact, urgency, resolution summary, resolution steps */
    record Template(String title, String description, String category, String service, String impact, String urgency, String summary, String steps) { }

    static final List<Template> TEMPLATES = List.of(
        new Template("Wi-Fi drops in hostel", "Wireless keeps disconnecting every few minutes in Ram Bhawan rooms.", "NETWORK", "CAMPUS_WIFI", "MEDIUM", "HIGH", "Replaced faulty access point AP-RB-12", "Swapped AP, re-applied SSID profile, verified roaming"),
        new Template("Wi-Fi slow in library", "Wireless very slow on the second floor of the library during evenings.", "NETWORK", "CAMPUS_WIFI", "MEDIUM", "MEDIUM", "Moved access points to less congested channels", "Ran spectrum scan, changed channels 6 to 11, raised band steering"),
        new Template("Cannot connect to campus Wi-Fi", "Laptop shows authentication failed when joining the campus Wi-Fi network.", "AUTHENTICATION", "CAMPUS_WIFI", "LOW", "MEDIUM", "Reset the user's RADIUS credentials", "Cleared cached profile, re-enrolled certificate, tested login"),
        new Template("Student portal login fails", "ERP student portal shows an error after entering the password.", "AUTHENTICATION", "STUDENT_PORTAL", "HIGH", "HIGH", "Cleared stuck SSO sessions on the identity provider", "Restarted SSO service, purged session cache, verified login"),
        new Template("Fee payment page error", "SWD payment page fails with an error while paying hostel fees.", "APPLICATION", "STUDENT_PORTAL", "HIGH", "HIGH", "Renewed expired payment gateway certificate", "Installed new TLS certificate, re-tested sandbox payment"),
        new Template("Portal very slow during registration", "Course registration pages take a minute to load.", "DATABASE", "STUDENT_PORTAL", "HIGH", "MEDIUM", "Added missing index on enrolment table", "Analysed slow query log, created index, load-tested"),
        new Template("Lab PC does not boot", "Computer 14 in CP lab shows a black screen on startup.", "HARDWARE", "LAB_COMPUTERS", "LOW", "MEDIUM", "Replaced failed power supply", "Diagnosed PSU, replaced unit, ran memory test"),
        new Template("Projector not detected in lab", "Lab projector is not detected by the instructor PC.", "HARDWARE", "LAB_COMPUTERS", "MEDIUM", "HIGH", "Replaced damaged HDMI cable", "Tested ports, swapped cable, updated display driver"),
        new Template("MATLAB licence error in lab", "MATLAB shows licence checkout failed on all lab computers.", "SOFTWARE", "LAB_COMPUTERS", "MEDIUM", "HIGH", "Restarted the licence server and renewed licence file", "Renewed licence, restarted FlexLM, verified checkout"),
        new Template("Email not syncing on phone", "Campus email stopped syncing on Android phone since yesterday.", "APPLICATION", "EMAIL", "LOW", "LOW", "Re-created the mail profile with modern authentication", "Removed account, re-added with OAuth, verified sync"),
        new Template("Emails going to spam", "Department circulars are landing in spam folders.", "SECURITY", "EMAIL", "MEDIUM", "MEDIUM", "Corrected the SPF and DKIM DNS records", "Updated DNS, re-signed domain key, sent test mail"),
        new Template("Library e-journal access denied", "Off-campus access to e-journals returns access denied.", "AUTHENTICATION", "LIBRARY", "MEDIUM", "MEDIUM", "Re-synchronised proxy user list", "Synced EZproxy accounts, cleared cache, verified login"),
        new Template("Library catalogue search down", "OPAC catalogue search returns a server error.", "APPLICATION", "LIBRARY", "MEDIUM", "LOW", "Restarted the catalogue application server", "Freed disk space, restarted Tomcat, re-indexed"),
        new Template("Network outage in academic block", "No wired internet in the B-block faculty rooms.", "NETWORK", "CAMPUS_NETWORK", "HIGH", "HIGH", "Replaced failed core switch uplink module", "Traced fibre, replaced SFP, restored uplink, monitored"),
        new Template("VPN connection fails", "Campus VPN client fails to connect from home.", "NETWORK", "CAMPUS_NETWORK", "LOW", "MEDIUM", "Updated VPN client profile", "Pushed new profile, updated client, verified tunnel"),
        new Template("Shared drive full", "Department shared drive reports no space left.", "STORAGE", "CAMPUS_NETWORK", "MEDIUM", "MEDIUM", "Extended storage volume and archived old files", "Archived files, extended LUN, set quota alerts"),
        new Template("Suspicious login alerts", "Several students received unusual sign-in alerts.", "SECURITY", "AUTHENTICATION", "HIGH", "HIGH", "Forced password reset and enabled MFA", "Blocked IPs, reset affected passwords, enforced MFA"),
        new Template("Password reset link not received", "Password reset email never arrives.", "AUTHENTICATION", "AUTHENTICATION", "LOW", "MEDIUM", "Fixed mail relay for the identity service", "Corrected relay settings, resent reset link"),
        new Template("Cloud storage sync stuck", "OneDrive sync is stuck on processing changes.", "CLOUD", "EMAIL", "LOW", "LOW", "Reset the sync client", "Unlinked account, cleared cache, re-linked"),
        new Template("Moodle quiz page crashes", "Quiz page crashes when many students submit together.", "SOFTWARE", "STUDENT_PORTAL", "HIGH", "HIGH", "Increased PHP worker pool and fixed timeout", "Raised workers, tuned timeouts, load-tested quiz"));

    private final JdbcTemplate jdbc;
    private final DemoUserSeeder users;

    public DemoIncidentSeeder(JdbcTemplate jdbc, DemoUserSeeder users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        users.run(arguments); // idempotent: makes sure the demo accounts exist first
        Integer existing = jdbc.queryForObject("select count(*) from incidents", Integer.class);
        if (existing == null || existing > 0) {
            log.info("Demo incident history skipped: the incidents table is not empty.");
            return;
        }
        List<Long> students = ids("STUDENT");
        List<Long> technicians = ids("TECHNICIAN");
        Long admin = jdbc.queryForObject("select min(id) from app_users where role = 'ADMIN'", Long.class);
        if (students.isEmpty() || technicians.isEmpty() || admin == null) {
            log.warn("Demo incident history skipped: demo users are missing (set DEMO_USER_PASSWORD).");
            return;
        }
        Random random = new Random(2026);  // fixed seed: the same history every time, so the demo script is repeatable
        Instant now = Instant.now();
        int created = 0;
        for (int n = 0; n < 60; n++) {
            Template t = TEMPLATES.get(n % TEMPLATES.size());
            boolean open = n >= 54;  // the six most recent stay open so SLA risk has something to show
            Instant at = open ? now.minus(Duration.ofMinutes((60L - n) * 25)) : now.minus(Duration.ofMinutes(30L * 24 * 60 - n * 700L + random.nextInt(240)));
            seed(t, at, open, n, students.get(n % students.size()), technicians.get(n % technicians.size()), admin, random);
            created++;
        }
        log.info("Seeded {} demo incidents across the last 30 days.", created);
    }

    private List<Long> ids(String role) {
        return jdbc.queryForList("select id from app_users where role = ? and enabled order by id", Long.class, role);
    }

    private void seed(Template t, Instant createdAt, boolean open, int n, Long reporter, Long technician, Long admin, Random random) {
        String priority = priority(t.impact(), t.urgency());
        int response = switch (priority) { case "P1" -> 5; case "P2" -> 15; case "P3" -> 60; default -> 240; };
        int resolution = switch (priority) { case "P1" -> 30; case "P2" -> 120; case "P3" -> 480; default -> 1440; };
        // Most incidents meet SLA; roughly one in eight is acknowledged or resolved late, for realistic compliance figures.
        boolean late = n % 8 == 3;
        Instant ack = createdAt.plus(Duration.ofSeconds((long) (response * 60 * (late ? 1.6 : 0.2 + random.nextDouble() * 0.6))));
        Instant resolved = createdAt.plus(Duration.ofSeconds((long) (resolution * 60 * (late ? 1.4 : 0.3 + random.nextDouble() * 0.6))));
        String status = open ? (n % 2 == 0 ? "IN_PROGRESS" : "ASSIGNED") : n % 5 == 0 ? "VERIFIED" : "CLOSED";
        Instant updated = open ? createdAt : resolved.plus(Duration.ofHours(2));
        Long id = jdbc.queryForObject("insert into incidents(title, description, reporter_id, category, affected_service, impact, urgency, priority, status, assigned_technician_id, "
                + "created_at, updated_at, acknowledged_at, resolved_at, verified_at, closed_at, criticality, response_target_minutes, resolution_target_minutes, response_deadline, resolution_deadline, "
                + "resolution_summary, resolution_steps, sla_paused_seconds) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0) returning id", Long.class,
                t.title(), t.description(), reporter, t.category(), t.service(), t.impact(), t.urgency(), priority, status, technician,
                ts(createdAt), ts(updated), open && status.equals("ASSIGNED") ? null : ts(ack), open ? null : ts(resolved),
                open ? null : ts(resolved.plus(Duration.ofHours(1))), "CLOSED".equals(status) ? ts(resolved.plus(Duration.ofHours(2))) : null,
                t.impact(), response, resolution, ts(createdAt.plus(Duration.ofMinutes(response))), ts(createdAt.plus(Duration.ofMinutes(resolution))),
                open ? null : t.summary(), open ? null : t.steps());
        history(id, reporter, "CREATED", "Incident reported.", createdAt);
        history(id, admin, "CLASSIFIED", "Category confirmed: " + t.category(), createdAt.plusSeconds(30));
        history(id, admin, "ASSIGNED", "Assigned to technician.", createdAt.plusSeconds(45));
        if (!open) {
            history(id, technician, "RESOLVED", "Resolution: " + t.summary(), resolved);
            history(id, reporter, "VERIFIED", "Reporter confirmed the issue is resolved.", resolved.plus(Duration.ofHours(1)));
            if ("CLOSED".equals(status)) history(id, admin, "CLOSED", "Administrator closed the verified incident.", resolved.plus(Duration.ofHours(2)));
            int rating = late ? 3 : 4 + random.nextInt(2);
            jdbc.update("insert into incident_feedback(rating, created_at, incident_id, student_id, comment) values (?,?,?,?,?)",
                    rating, ts(resolved.plus(Duration.ofHours(1))), id, reporter, rating >= 4 ? "Quick and helpful." : "Fixed, but took a while.");
        }
    }

    private void history(Long incident, Long actor, String action, String description, Instant at) {
        jdbc.update("insert into incident_history(actor_id, created_at, incident_id, description, action, visibility) values (?,?,?,?,?, 'STUDENT_VISIBLE')",
                actor, ts(at), incident, description, action);
    }

    /** Mirrors the default multiplicative strategy (Low/Medium/High = 1/2/3, impact × urgency × criticality, P1 ≥ 18, P2 ≥ 12, P3 ≥ 4); the seed uses impact as the criticality proxy. */
    static String priority(String impact, String urgency) {
        int score = level(impact) * level(urgency) * level(impact);
        return score >= 18 ? "P1" : score >= 12 ? "P2" : score >= 4 ? "P3" : "P4";
    }

    private static int level(String l) { return switch (l) { case "HIGH" -> 3; case "MEDIUM" -> 2; default -> 1; }; }

    private static Timestamp ts(Instant i) { return i == null ? null : Timestamp.from(i); }
}
