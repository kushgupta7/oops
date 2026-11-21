package com.bits.itsm.incident;

import jakarta.persistence.*;

@Entity @Table(name = "catalog_entries")
public class CatalogEntry {
    @Id private String id;
    @Column(nullable = false) private String kind;
    @Column(nullable = false) private String code;
    @Column(nullable = false, length = 100) private String label;
    @Column(nullable = false) private boolean enabled = true;
    @Enumerated(EnumType.STRING) private Incident.Level criticality;
    @Column(length = 2000) private String keywords;

    // Multiplies the priority SLA targets for this service: 0.5 = twice as fast, 2.0 = twice as long.
    @Column(nullable = false) private double slaFactor = 1.0;
    protected CatalogEntry() { }
    public String getId() { return id; }
    public String getKind() { return kind; }
    public String getCode() { return code; }
    public String getLabel() { return label; }
    public boolean isEnabled() { return enabled; }
    public Incident.Level getCriticality() { return criticality; }
    public String getKeywords() { return keywords; }

    public double getSlaFactor() { return slaFactor; }
    public void configureSla(double factor) {
        if (!"SERVICE".equals(kind)) throw new IllegalArgumentException("Only IT services have an SLA factor.");
        if (!Double.isFinite(factor) || factor < 0.25 || factor > 4.0) throw new IllegalArgumentException("SLA factor must be between 0.25 and 4.");
        slaFactor = factor;
    }
    public void update(String label, boolean enabled, Incident.Level criticality) {
        update(label, enabled, criticality, this.keywords);
    }

    public void update(String label, boolean enabled, Incident.Level criticality, String keywords) {
        if ("SERVICE".equals(kind) && enabled && criticality == null)
            throw new IllegalArgumentException("Configure business criticality before enabling this service.");
        this.label = Incident.text(label, 1, 100);
        this.enabled = enabled;
        this.criticality = criticality;
        this.keywords = keywords != null ? keywords.trim() : null;
    }
}
