package com.clinconnect.chatbot.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "location")
public class Location {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "time_zone", nullable = false)
    private String timeZone;

    @Column(nullable = false)
    private boolean active;

    protected Location() {
    }

    public Location(String id, String slug, String displayName, String timeZone, boolean active) {
        this.id = id;
        this.slug = slug;
        this.displayName = displayName;
        this.timeZone = timeZone;
        this.active = active;
    }

    public String getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public boolean isActive() {
        return active;
    }
}
