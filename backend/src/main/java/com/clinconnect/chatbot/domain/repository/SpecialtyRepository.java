package com.clinconnect.chatbot.domain.repository;

import com.clinconnect.chatbot.domain.model.Specialty;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecialtyRepository extends JpaRepository<Specialty, String> {

    Optional<Specialty> findBySlugIgnoreCaseAndActiveTrue(String slug);

    Optional<Specialty> findByDisplayNameIgnoreCaseAndActiveTrue(String displayName);
}
