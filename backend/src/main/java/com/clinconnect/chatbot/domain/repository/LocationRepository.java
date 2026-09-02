package com.clinconnect.chatbot.domain.repository;

import com.clinconnect.chatbot.domain.model.Location;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, String> {

    List<Location> findByActiveTrueOrderByDisplayNameAsc();

    Optional<Location> findBySlugIgnoreCaseAndActiveTrue(String slug);

    Optional<Location> findByDisplayNameIgnoreCaseAndActiveTrue(String displayName);
}
