package com.clinconnect.chatbot.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "department")
public class Department {

    @Id
    private String id;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "location_id", nullable = false)
    private String locationId;

    @Column(name = "specialty_id", nullable = false)
    private String specialtyId;

    @Column
    private String note;

    @Column(nullable = false)
    private boolean active;

    protected Department() {
    }

    public Department(
            String id, String displayName, String locationId, String specialtyId, String note, boolean active) {
        this.id = id;
        this.displayName = displayName;
        this.locationId = locationId;
        this.specialtyId = specialtyId;
        this.note = note;
        this.active = active;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLocationId() {
        return locationId;
    }

    public String getSpecialtyId() {
        return specialtyId;
    }

    public String getNote() {
        return note;
    }

    public boolean isActive() {
        return active;
    }
}
