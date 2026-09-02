package com.clinconnect.chatbot.domain.repository;

import com.clinconnect.chatbot.domain.model.Department;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, String> {

    Optional<Department> findByLocationIdAndSpecialtyIdAndActiveTrue(String locationId, String specialtyId);
}
