package com.clinconnect.chatbot.domain.repository;

import com.clinconnect.chatbot.domain.model.LocationSpecialty;
import com.clinconnect.chatbot.domain.model.LocationSpecialtyId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LocationSpecialtyRepository extends JpaRepository<LocationSpecialty, LocationSpecialtyId> {

    List<LocationSpecialty> findBySpecialtyIdAndActiveTrue(String specialtyId);

    List<LocationSpecialty> findByLocationIdAndActiveTrue(String locationId);

    @Query("""
            select ls.locationId from LocationSpecialty ls
            join Location l on l.id = ls.locationId
            where ls.specialtyId = :specialtyId and ls.active = true and l.active = true
            """)
    List<String> findActiveLocationIdsOfferingSpecialty(@Param("specialtyId") String specialtyId);
}
