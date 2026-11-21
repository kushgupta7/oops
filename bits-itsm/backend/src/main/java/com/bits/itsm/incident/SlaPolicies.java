package com.bits.itsm.incident;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SlaPolicies implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final SlaPolicyRepository policies;
    private final CatalogService catalog;
    public SlaPolicies(JdbcTemplate jdbc, SlaPolicyRepository policies, CatalogService catalog) { this.jdbc = jdbc; this.policies = policies; this.catalog = catalog; }
    @Override @Transactional public void run(ApplicationArguments args) {
        jdbc.update("insert into sla_policies(priority,response_minutes,resolution_minutes) values ('P1',5,30),('P2',15,120),('P3',60,480),('P4',240,1440) on conflict (priority) do nothing");
    }
    public void capture(Incident incident) {
        SlaPolicy policy = policies.findById(incident.getPriority()).orElseThrow(() -> new com.bits.itsm.exception.MissingSlaConfigurationException("No SLA policy is configured for priority " + incident.getPriority() + "."));
        // "Different IT services may have different SLAs": the service's factor scales the priority targets.
        incident.captureSla(policy, incident.getAffectedService() == null ? 1.0 : catalog.get("SERVICE", incident.getAffectedService().name()).getSlaFactor());
    }
}
