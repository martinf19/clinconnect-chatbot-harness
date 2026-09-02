package com.clinconnect.chatbot.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "location_specialty")
@IdClass(LocationSpecialtyId.class)
public class LocationSpecialty {

    @Id
    @Column(name = "location_id")
    private String locationId;

    @Id
    @Column(name = "specialty_id")
    private String specialtyId;

    @Column(nullable = false)
    private boolean active;

    protected LocationSpecialty() {
    }

    public LocationSpecialty(String locationId, String specialtyId, boolean active) {
        this.locationId = locationId;
        this.specialtyId = specialtyId;
        this.active = active;
    }

    public String getLocationId() {
        return locationId;
    }

    public String getSpecialtyId() {
        return specialtyId;
    }

    public boolean isActive() {
        return active;
    }
}
