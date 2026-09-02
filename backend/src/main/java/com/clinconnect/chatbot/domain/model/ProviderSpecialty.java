package com.clinconnect.chatbot.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "provider_specialty")
@IdClass(ProviderSpecialtyId.class)
public class ProviderSpecialty {

    @Id
    @Column(name = "provider_id")
    private String providerId;

    @Id
    @Column(name = "specialty_id")
    private String specialtyId;

    protected ProviderSpecialty() {
    }

    public ProviderSpecialty(String providerId, String specialtyId) {
        this.providerId = providerId;
        this.specialtyId = specialtyId;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getSpecialtyId() {
        return specialtyId;
    }
}
