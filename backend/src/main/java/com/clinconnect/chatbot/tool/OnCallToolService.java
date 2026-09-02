package com.clinconnect.chatbot.tool;

import com.clinconnect.chatbot.config.ChatbotConfigLoader;
import com.clinconnect.chatbot.domain.model.CoverageAssignment;
import com.clinconnect.chatbot.domain.repository.CoverageAssignmentRepository;
import com.clinconnect.chatbot.security.AuthenticatedSubject;
import com.clinconnect.chatbot.security.AuthorizationService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * get_oncall_now (FR-004) and get_oncall_schedule (FR-005). Both tools
 * receive already-resolved trusted instants (spring_clock /
 * spring_time_normalizer per config/tools.yaml) — this service never reads
 * the wall clock itself.
 */
@Service
public class OnCallToolService {

    private final CoverageAssignmentRepository coverageAssignmentRepository;
    private final AuthorizationService authorizationService;
    private final String requiredScope;

    public OnCallToolService(
            CoverageAssignmentRepository coverageAssignmentRepository,
            AuthorizationService authorizationService,
            ChatbotConfigLoader chatbotConfigLoader) {
        this.coverageAssignmentRepository = coverageAssignmentRepository;
        this.authorizationService = authorizationService;
        this.requiredScope = chatbotConfigLoader.config().toolsById().get("get_oncall_now").authorizationScope();
    }

    /**
     * 0 results -> NO_MATCH; exactly 1 -> FOUND; more than 1 (role did not
     * narrow it down) -> AMBIGUOUS (FR-004 acceptance criteria).
     */
    public ToolResult<CoverageAssignment> getOnCallNow(
            AuthenticatedSubject subject,
            String locationId,
            String specialtyId,
            String roleCode,
            Instant referenceTime) {
        authorizationService.authorize(subject, requiredScope);
        List<CoverageAssignment> matches =
                coverageAssignmentRepository.findActiveAt(locationId, specialtyId, referenceTime, roleCode);
        if (matches.isEmpty()) {
            return ToolResult.noMatch();
        }
        if (matches.size() == 1) {
            return ToolResult.found(matches.get(0));
        }
        return ToolResult.ambiguous(matches.stream().map(CoverageAssignment::getId).toList());
    }

    /** Schedule listing: NO_MATCH if nothing overlaps the interval, else FOUND with every overlapping shift. */
    public ToolResult<List<CoverageAssignment>> getOnCallSchedule(
            AuthenticatedSubject subject,
            String locationId,
            String specialtyId,
            String roleCode,
            Instant startAt,
            Instant endAt) {
        authorizationService.authorize(subject, requiredScope);
        List<CoverageAssignment> matches =
                coverageAssignmentRepository.findOverlapping(locationId, specialtyId, startAt, endAt, roleCode);
        if (matches.isEmpty()) {
            return ToolResult.noMatch();
        }
        return ToolResult.found(matches);
    }
}
