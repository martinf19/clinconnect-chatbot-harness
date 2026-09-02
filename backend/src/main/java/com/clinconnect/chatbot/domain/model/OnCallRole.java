package com.clinconnect.chatbot.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "on_call_role")
public class OnCallRole {

    @Id
    private String code;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "definition_text", nullable = false)
    private String definitionText;

    @Column(nullable = false)
    private boolean active;

    protected OnCallRole() {
    }

    public OnCallRole(String code, String displayName, String definitionText, boolean active) {
        this.code = code;
        this.displayName = displayName;
        this.definitionText = definitionText;
        this.active = active;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDefinitionText() {
        return definitionText;
    }

    public boolean isActive() {
        return active;
    }
}
