package com.clinconnect.chatbot.domain.repository;

import com.clinconnect.chatbot.domain.model.ConsultRoutingCategory;
import com.clinconnect.chatbot.domain.model.ConsultRoutingRule;
import com.clinconnect.chatbot.domain.model.DeclaredUrgency;
import com.clinconnect.chatbot.domain.model.TimeContext;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConsultRoutingRuleRepository extends JpaRepository<ConsultRoutingRule, String> {

    @Query("""
            select r from ConsultRoutingRule r
            where r.locationId = :locationId and r.specialtyId = :specialtyId
              and r.category = :category and r.active = true
              and (:timeContext is null or r.timeContext is null or r.timeContext = :timeContext)
              and (:declaredUrgency is null or r.declaredUrgency is null or r.declaredUrgency = :declaredUrgency)
            order by r.id asc
            """)
    List<ConsultRoutingRule> findApplicableRules(
            @Param("locationId") String locationId,
            @Param("specialtyId") String specialtyId,
            @Param("category") ConsultRoutingCategory category,
            @Param("timeContext") TimeContext timeContext,
            @Param("declaredUrgency") DeclaredUrgency declaredUrgency);
}
