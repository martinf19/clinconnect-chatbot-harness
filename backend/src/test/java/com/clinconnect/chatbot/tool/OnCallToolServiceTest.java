package com.clinconnect.chatbot.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.clinconnect.chatbot.domain.model.CoverageAssignment;
import com.clinconnect.chatbot.domain.repository.CoverageAssignmentRepository;
import com.clinconnect.chatbot.security.AuthenticatedSubject;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coverage fixtures are fixed literal Instants (never Instant.now()), per
 * docs/10-EVALUATION-PLAN.md: "Coverage fixtures must be created relative to
 * an injectable test clock or fixed integration-test clock, not the
 * workstation wall clock." {@code @Transactional} rolls each test's inserts
 * back so tests don't see each other's fixtures.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class OnCallToolServiceTest {

    @Autowired
    private OnCallToolService onCallToolService;

    @Autowired
    private CoverageAssignmentRepository coverageAssignmentRepository;

    private final AuthenticatedSubject subject = new AuthenticatedSubject("local-user-1", "CHATBOT_USER");

    private static final Instant SHIFT_START = Instant.parse("2026-06-15T15:00:00Z");
    private static final Instant SHIFT_END = Instant.parse("2026-06-16T03:00:00Z");
    private static final Instant WITHIN_SHIFT = Instant.parse("2026-06-15T20:00:00Z");
    private static final Instant OUTSIDE_SHIFT = Instant.parse("2026-06-17T20:00:00Z");

    @Test
    void findsTheSingleCoveringProviderAtTheReferenceInstant() {
        coverageAssignmentRepository.save(new CoverageAssignment(
                "assignment-1", "provider-avery-chen", "spec-neurology", "loc-oakland",
                "PRIMARY_ONCALL", SHIFT_START, SHIFT_END));

        ToolResult<CoverageAssignment> result = onCallToolService.getOnCallNow(
                subject, "loc-oakland", "spec-neurology", null, WITHIN_SHIFT);

        assertThat(result.status()).isEqualTo(ToolResultStatus.FOUND);
        assertThat(result.data().getProviderId()).isEqualTo("provider-avery-chen");
    }

    @Test
    void returnsNoMatchWhenNothingCoversTheReferenceInstant() {
        coverageAssignmentRepository.save(new CoverageAssignment(
                "assignment-1", "provider-avery-chen", "spec-neurology", "loc-oakland",
                "PRIMARY_ONCALL", SHIFT_START, SHIFT_END));

        ToolResult<CoverageAssignment> result = onCallToolService.getOnCallNow(
                subject, "loc-oakland", "spec-neurology", null, OUTSIDE_SHIFT);

        assertThat(result.status()).isEqualTo(ToolResultStatus.NO_MATCH);
    }

    @Test
    void multipleOverlappingRolesWithoutARequestedRoleAreAmbiguous() {
        coverageAssignmentRepository.saveAll(List.of(
                new CoverageAssignment("assignment-primary", "provider-avery-chen", "spec-neurology", "loc-oakland",
                        "PRIMARY_ONCALL", SHIFT_START, SHIFT_END),
                new CoverageAssignment("assignment-backup", "provider-jordan-lee", "spec-neurology", "loc-oakland",
                        "BACKUP_ONCALL", SHIFT_START, SHIFT_END)));

        ToolResult<CoverageAssignment> result = onCallToolService.getOnCallNow(
                subject, "loc-oakland", "spec-neurology", null, WITHIN_SHIFT);

        assertThat(result.status()).isEqualTo(ToolResultStatus.AMBIGUOUS);
        assertThat(result.ambiguousCandidateIds())
                .containsExactlyInAnyOrder("assignment-primary", "assignment-backup");
    }

    @Test
    void requestingASpecificRoleNarrowsAnOtherwiseAmbiguousResult() {
        coverageAssignmentRepository.saveAll(List.of(
                new CoverageAssignment("assignment-primary", "provider-avery-chen", "spec-neurology", "loc-oakland",
                        "PRIMARY_ONCALL", SHIFT_START, SHIFT_END),
                new CoverageAssignment("assignment-backup", "provider-jordan-lee", "spec-neurology", "loc-oakland",
                        "BACKUP_ONCALL", SHIFT_START, SHIFT_END)));

        ToolResult<CoverageAssignment> result = onCallToolService.getOnCallNow(
                subject, "loc-oakland", "spec-neurology", "PRIMARY_ONCALL", WITHIN_SHIFT);

        assertThat(result.status()).isEqualTo(ToolResultStatus.FOUND);
        assertThat(result.data().getProviderId()).isEqualTo("provider-avery-chen");
    }

    @Test
    void scheduleReturnsEveryShiftOverlappingTheInterval() {
        coverageAssignmentRepository.save(new CoverageAssignment(
                "assignment-1", "provider-avery-chen", "spec-neurology", "loc-oakland",
                "PRIMARY_ONCALL", SHIFT_START, SHIFT_END));

        ToolResult<List<CoverageAssignment>> result = onCallToolService.getOnCallSchedule(
                subject, "loc-oakland", "spec-neurology", null,
                Instant.parse("2026-06-15T00:00:00Z"), Instant.parse("2026-06-17T00:00:00Z"));

        assertThat(result.status()).isEqualTo(ToolResultStatus.FOUND);
        assertThat(result.data()).extracting(CoverageAssignment::getId).containsExactly("assignment-1");
    }

    @Test
    void scheduleIsNoMatchWhenNothingOverlapsTheInterval() {
        coverageAssignmentRepository.save(new CoverageAssignment(
                "assignment-1", "provider-avery-chen", "spec-neurology", "loc-oakland",
                "PRIMARY_ONCALL", SHIFT_START, SHIFT_END));

        ToolResult<List<CoverageAssignment>> result = onCallToolService.getOnCallSchedule(
                subject, "loc-oakland", "spec-neurology", null,
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-02T00:00:00Z"));

        assertThat(result.status()).isEqualTo(ToolResultStatus.NO_MATCH);
    }
}
