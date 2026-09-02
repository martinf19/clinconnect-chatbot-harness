package com.clinconnect.chatbot.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "coverage_assignment")
public class CoverageAssignment {

    @Id
    private String id;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Column(name = "specialty_id", nullable = false)
    private String specialtyId;

    @Column(name = "location_id", nullable = false)
    private String locationId;

    @Column(name = "role_code", nullable = false)
    private String roleCode;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    protected CoverageAssignment() {
    }

    public CoverageAssignment(
            String id,
            String providerId,
            String specialtyId,
            String locationId,
            String roleCode,
            Instant startsAt,
            Instant endsAt) {
        this.id = id;
        this.providerId = providerId;
        this.specialtyId = specialtyId;
        this.locationId = locationId;
        this.roleCode = roleCode;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public String getId() {
        return id;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getSpecialtyId() {
        return specialtyId;
    }

    public String getLocationId() {
        return locationId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }
}
