package com.clinconnect.chatbot.domain.model;

import java.io.Serializable;
import java.util.Objects;

public class LocationSpecialtyId implements Serializable {

    private String locationId;
    private String specialtyId;

    protected LocationSpecialtyId() {
    }

    public LocationSpecialtyId(String locationId, String specialtyId) {
        this.locationId = locationId;
        this.specialtyId = specialtyId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LocationSpecialtyId that)) {
            return false;
        }
        return Objects.equals(locationId, that.locationId) && Objects.equals(specialtyId, that.specialtyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locationId, specialtyId);
    }
}
