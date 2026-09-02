package com.clinconnect.chatbot.domain.repository;

import com.clinconnect.chatbot.domain.model.ContactMethod;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactMethodRepository extends JpaRepository<ContactMethod, String> {

    List<ContactMethod> findByProviderIdAndActiveTrue(String providerId);
}
