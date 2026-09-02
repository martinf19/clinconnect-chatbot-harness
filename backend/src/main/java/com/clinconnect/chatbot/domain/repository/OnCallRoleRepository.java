package com.clinconnect.chatbot.domain.repository;

import com.clinconnect.chatbot.domain.model.OnCallRole;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnCallRoleRepository extends JpaRepository<OnCallRole, String> {

    Optional<OnCallRole> findByCodeIgnoreCaseAndActiveTrue(String code);

    Optional<OnCallRole> findByDisplayNameIgnoreCaseAndActiveTrue(String displayName);
}
