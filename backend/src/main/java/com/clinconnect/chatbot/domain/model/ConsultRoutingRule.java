package com.clinconnect.chatbot.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "consult_routing_rule")
public class ConsultRoutingRule {

    @Id
    private String id;

    @Column(name = "location_id", nullable = false)
    private String locationId;

    @Column(name = "specialty_id", nullable = false)
    private String specialtyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConsultRoutingCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_context")
    private TimeContext timeContext;

    @Enumerated(EnumType.STRING)
    @Column(name = "declared_urgency")
    private DeclaredUrgency declaredUrgency;

    @Column(name = "routing_text", nullable = false)
    private String routingText;

    @Column(nullable = false)
    private boolean active;

    protected ConsultRoutingRule() {
    }

    public ConsultRoutingRule(
            String id,
            String locationId,
            String specialtyId,
            ConsultRoutingCategory category,
            TimeContext timeContext,
            DeclaredUrgency declaredUrgency,
            String routingText,
            boolean active) {
        this.id = id;
        this.locationId = locationId;
        this.specialtyId = specialtyId;
        this.category = category;
        this.timeContext = timeContext;
        this.declaredUrgency = declaredUrgency;
        this.routingText = routingText;
        this.active = active;
    }

    public String getId() {
        return id;
    }

    public String getLocationId() {
        return locationId;
    }

    public String getSpecialtyId() {
        return specialtyId;
    }

    public ConsultRoutingCategory getCategory() {
        return category;
    }

    public TimeContext getTimeContext() {
        return timeContext;
    }

    public DeclaredUrgency getDeclaredUrgency() {
        return declaredUrgency;
    }

    public String getRoutingText() {
        return routingText;
    }

    public boolean isActive() {
        return active;
    }
}
