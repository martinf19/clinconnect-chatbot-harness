package com.clinconnect.chatbot.domain.repository;

import com.clinconnect.chatbot.domain.model.ProviderSpecialty;
import com.clinconnect.chatbot.domain.model.ProviderSpecialtyId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderSpecialtyRepository extends JpaRepository<ProviderSpecialty, ProviderSpecialtyId> {
}
