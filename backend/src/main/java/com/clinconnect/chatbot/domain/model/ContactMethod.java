package com.clinconnect.chatbot.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "contact_method")
public class ContactMethod {

    @Id
    private String id;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", nullable = false)
    private ContactType contactType;

    @Column(name = "contact_value", nullable = false)
    private String value;

    @Column(nullable = false)
    private boolean active;

    protected ContactMethod() {
    }

    public ContactMethod(String id, String providerId, ContactType contactType, String value, boolean active) {
        this.id = id;
        this.providerId = providerId;
        this.contactType = contactType;
        this.value = value;
        this.active = active;
    }

    public String getId() {
        return id;
    }

    public String getProviderId() {
        return providerId;
    }

    public ContactType getContactType() {
        return contactType;
    }

    public String getValue() {
        return value;
    }

    public boolean isActive() {
        return active;
    }
}
