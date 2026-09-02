package com.clinconnect.chatbot.domain.repository;

import com.clinconnect.chatbot.domain.model.CoverageAssignment;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CoverageAssignmentRepository extends JpaRepository<CoverageAssignment, String> {

    @Query("""
            select c from CoverageAssignment c
            where c.locationId = :locationId and c.specialtyId = :specialtyId
              and c.startsAt <= :referenceTime and c.endsAt > :referenceTime
              and (:roleCode is null or c.roleCode = :roleCode)
            """)
    List<CoverageAssignment> findActiveAt(
            @Param("locationId") String locationId,
            @Param("specialtyId") String specialtyId,
            @Param("referenceTime") Instant referenceTime,
            @Param("roleCode") String roleCode);

    @Query("""
            select c from CoverageAssignment c
            where c.locationId = :locationId and c.specialtyId = :specialtyId
              and c.startsAt < :endAt and c.endsAt > :startAt
              and (:roleCode is null or c.roleCode = :roleCode)
            order by c.startsAt asc
            """)
    List<CoverageAssignment> findOverlapping(
            @Param("locationId") String locationId,
            @Param("specialtyId") String specialtyId,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("roleCode") String roleCode);
}
