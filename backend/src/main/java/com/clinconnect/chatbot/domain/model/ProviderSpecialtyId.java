package com.clinconnect.chatbot.domain.model;

import java.io.Serializable;
import java.util.Objects;

public class ProviderSpecialtyId implements Serializable {

    private String providerId;
    private String specialtyId;

    protected ProviderSpecialtyId() {
    }

    public ProviderSpecialtyId(String providerId, String specialtyId) {
        this.providerId = providerId;
        this.specialtyId = specialtyId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProviderSpecialtyId that)) {
            return false;
        }
        return Objects.equals(providerId, that.providerId) && Objects.equals(specialtyId, that.specialtyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerId, specialtyId);
    }
}
