package com.clinconnect.chatbot.domain.repository;

import com.clinconnect.chatbot.domain.model.Provider;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderRepository extends JpaRepository<Provider, String> {

    List<Provider> findByDisplayNameIgnoreCaseAndActiveTrue(String displayName);
}
