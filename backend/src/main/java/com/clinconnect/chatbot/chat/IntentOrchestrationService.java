package com.clinconnect.chatbot.chat;

import com.clinconnect.chatbot.canonicalization.CanonicalizationResult;
import com.clinconnect.chatbot.canonicalization.CanonicalizationService;
import com.clinconnect.chatbot.canonicalization.CanonicalizationStatus;
import com.clinconnect.chatbot.config.ChatbotConfigLoader;
import com.clinconnect.chatbot.domain.model.ConsultRoutingRule;
import com.clinconnect.chatbot.domain.model.ContactMethod;
import com.clinconnect.chatbot.domain.model.ContactType;
import com.clinconnect.chatbot.domain.model.CoverageAssignment;
import com.clinconnect.chatbot.domain.model.Department;
import com.clinconnect.chatbot.domain.model.Location;
import com.clinconnect.chatbot.domain.model.OnCallRole;
import com.clinconnect.chatbot.domain.model.Provider;
import com.clinconnect.chatbot.domain.model.Specialty;
import com.clinconnect.chatbot.domain.repository.CoverageAssignmentRepository;
import com.clinconnect.chatbot.domain.repository.LocationRepository;
import com.clinconnect.chatbot.domain.repository.OnCallRoleRepository;
import com.clinconnect.chatbot.domain.repository.ProviderRepository;
import com.clinconnect.chatbot.domain.repository.SpecialtyRepository;
import com.clinconnect.chatbot.format.FactualResponseFormatter;
import com.clinconnect.chatbot.interpretation.InterpretationParameters;
import com.clinconnect.chatbot.interpretation.ProviderReferenceKind;
import com.clinconnect.chatbot.security.AuthenticatedSubject;
import com.clinconnect.chatbot.session.ClarificationOption;
import com.clinconnect.chatbot.session.ClarificationParameterName;
import com.clinconnect.chatbot.session.ClarificationReason;
import com.clinconnect.chatbot.session.LastQueryContext;
import com.clinconnect.chatbot.session.LastResultContext;
import com.clinconnect.chatbot.time.TimeInterval;
import com.clinconnect.chatbot.time.TimeIntervalResolver;
import com.clinconnect.chatbot.tool.ConsultRoutingToolService;
import com.clinconnect.chatbot.tool.ContactInfoToolService;
import com.clinconnect.chatbot.tool.DepartmentInfoToolService;
import com.clinconnect.chatbot.tool.LocationToolService;
import com.clinconnect.chatbot.tool.OnCallToolService;
import com.clinconnect.chatbot.tool.RoleExplanationToolService;
import com.clinconnect.chatbot.tool.SpecialtyToolService;
import com.clinconnect.chatbot.tool.ToolResult;
import com.clinconnect.chatbot.tool.ToolResultStatus;
import java.time.Clock;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Deterministically maps one already-language-interpreted request (a
 * canonical intent_id plus raw parameters, docs/05-INTENT-CATALOG.md) to a
 * canonicalized, authorized, executed result — or a clarification/NO_MATCH
 * decision (FR-012/FR-014). This is the "canonicalize -> decide
 * clarification vs execution -> authorize -> derive tool -> execute" core
 * of docs/03-ARCHITECTURE.md's POC Request Ordering (steps 7-11).
 *
 * <p>Callers must only invoke {@link #resolve} after confirming Python's
 * reported {@code missing_parameters} is empty for always-required fields
 * (ChatOrchestrationService does this once, generically, before dispatch —
 * Phase 4 also lets it silently backfill a missing field from {@link
 * SessionResolutionContext#lastQuery()} before that check, see its own
 * Javadoc). Every handler below may assume its intent's always-required raw
 * fields are non-null. BACKEND_UNIQUE_OR_CLARIFY location handling is the
 * exception — it is never in {@code missing_parameters} (config/intents
 * .yaml marks it optional) and is resolved here via {@link
 * CanonicalizationService#resolveLocationForSpecialty}, with a Phase 4
 * fallback to the session's last resolved location when it is omitted,
 * still ambiguous, and the prior location remains a valid candidate
 * (FR-013 "Same location.").
 *
 * <p><b>Trusted selections (Phase 4):</b> when resuming a {@code
 * SELECTED_OPTION} clarification reply for {@code LOCATION_TEXT} or {@code
 * ROLE_TEXT}, {@link SessionResolutionContext#trustedSelectionParameter()}
 * carries the canonical id straight through instead of re-canonicalizing
 * the chosen option's label — generalizing the Phase 3 {@code
 * PROVIDER_REFERENCE} fix (see {@link SessionResolutionContext} Javadoc).
 */
@Service
public class IntentOrchestrationService {

    private final CanonicalizationService canonicalizationService;
    private final ChatbotConfigLoader chatbotConfigLoader;
    private final LocationRepository locationRepository;
    private final SpecialtyRepository specialtyRepository;
    private final ProviderRepository providerRepository;
    private final OnCallRoleRepository onCallRoleRepository;
    private final CoverageAssignmentRepository coverageAssignmentRepository;
    private final LocationToolService locationToolService;
    private final SpecialtyToolService specialtyToolService;
    private final DepartmentInfoToolService departmentInfoToolService;
    private final OnCallToolService onCallToolService;
    private final ContactInfoToolService contactInfoToolService;
    private final ConsultRoutingToolService consultRoutingToolService;
    private final RoleExplanationToolService roleExplanationToolService;
    private final FactualResponseFormatter formatter;
    private final TimeIntervalResolver timeIntervalResolver;
    private final Clock clock;

    public IntentOrchestrationService(
            CanonicalizationService canonicalizationService,
            ChatbotConfigLoader chatbotConfigLoader,
            LocationRepository locationRepository,
            SpecialtyRepository specialtyRepository,
            ProviderRepository providerRepository,
            OnCallRoleRepository onCallRoleRepository,
            CoverageAssignmentRepository coverageAssignmentRepository,
            LocationToolService locationToolService,
            SpecialtyToolService specialtyToolService,
            DepartmentInfoToolService departmentInfoToolService,
            OnCallToolService onCallToolService,
            ContactInfoToolService contactInfoToolService,
            ConsultRoutingToolService consultRoutingToolService,
            RoleExplanationToolService roleExplanationToolService,
            FactualResponseFormatter formatter,
            TimeIntervalResolver timeIntervalResolver,
            Clock clock) {
        this.canonicalizationService = canonicalizationService;
        this.chatbotConfigLoader = chatbotConfigLoader;
        this.locationRepository = locationRepository;
        this.specialtyRepository = specialtyRepository;
        this.providerRepository = providerRepository;
        this.onCallRoleRepository = onCallRoleRepository;
        this.coverageAssignmentRepository = coverageAssignmentRepository;
        this.locationToolService = locationToolService;
        this.specialtyToolService = specialtyToolService;
        this.departmentInfoToolService = departmentInfoToolService;
        this.onCallToolService = onCallToolService;
        this.contactInfoToolService = contactInfoToolService;
        this.consultRoutingToolService = consultRoutingToolService;
        this.roleExplanationToolService = roleExplanationToolService;
        this.formatter = formatter;
        this.timeIntervalResolver = timeIntervalResolver;
        this.clock = clock;
    }

    public IntentOutcome resolve(
            String intentId, InterpretationParameters params, AuthenticatedSubject subject, SessionResolutionContext ctx) {
        // NFR-006 / docs/09 "Tool Security": deterministic, config-validated dispatch — fail
        // closed on any intent_id without a config/intents.yaml mapping.
        if (chatbotConfigLoader.config().intentsById().get(intentId) == null) {
            throw new UnknownIntentException(intentId);
        }
        SessionResolutionContext effectiveCtx = ctx == null ? SessionResolutionContext.none() : ctx;
        return switch (intentId) {
            case "get_locations" -> handleGetLocations(subject);
            case "get_specialties" -> handleGetSpecialties(params, subject);
            case "get_department_info" -> handleGetDepartmentInfo(params, subject);
            case "get_oncall_now" -> handleGetOncallNow(params, subject, effectiveCtx);
            case "get_oncall_schedule" -> handleGetOncallSchedule(params, subject, effectiveCtx);
            case "get_contact_info" -> handleGetContactInfo(params, subject, effectiveCtx);
            case "get_pcconsult_info", "triage_consult" -> handleConsultRouting(params, subject);
            case "chart_chat_guidance" -> handleChartChatGuidance(params, subject);
            case "role_explanation" -> handleRoleExplanation(params, subject);
            default -> throw new UnknownIntentException(intentId);
        };
    }

    private IntentOutcome handleGetLocations(AuthenticatedSubject subject) {
        ToolResult<List<Location>> result = locationToolService.getLocations(subject);
        return new IntentOutcome.Answer(formatter.formatLocations(result.data()), null);
    }

    private IntentOutcome handleGetSpecialties(InterpretationParameters params, AuthenticatedSubject subject) {
        CanonicalizationResult location = canonicalizationService.resolveLocation(params.locationText());
        if (location.status() == CanonicalizationStatus.NOT_FOUND) {
            return new IntentOutcome.NoMatch();
        }
        Location locationEntity = locationRepository.findById(location.canonicalId()).orElseThrow();
        ToolResult<List<Specialty>> result = specialtyToolService.getSpecialties(subject, location.canonicalId());
        return new IntentOutcome.Answer(
                formatter.formatSpecialties(locationEntity, result.data()),
                null,
                new ResolvedQueryContext(locationEntity.getDisplayName(), null, null));
    }

    private IntentOutcome handleGetDepartmentInfo(InterpretationParameters params, AuthenticatedSubject subject) {
        CanonicalizationResult location = canonicalizationService.resolveLocation(params.locationText());
        if (location.status() == CanonicalizationStatus.NOT_FOUND) {
            return new IntentOutcome.NoMatch();
        }
        CanonicalizationResult specialty = canonicalizationService.resolveSpecialty(params.specialtyText());
        if (specialty.status() == CanonicalizationStatus.NOT_FOUND) {
            return new IntentOutcome.NoMatch();
        }
        ToolResult<Department> result =
                departmentInfoToolService.getDepartmentInfo(subject, location.canonicalId(), specialty.canonicalId());
        Location locationEntity = locationRepository.findById(location.canonicalId()).orElseThrow();
        Specialty specialtyEntity = specialtyRepository.findById(specialty.canonicalId()).orElseThrow();
        ResolvedQueryContext resolvedContext =
                new ResolvedQueryContext(locationEntity.getDisplayName(), specialtyEntity.getDisplayName(), null);
        if (result.status() == ToolResultStatus.NO_MATCH) {
            return new IntentOutcome.NoMatch(resolvedContext);
        }
        return new IntentOutcome.Answer(
                formatter.formatDepartmentInfo(result.data(), locationEntity, specialtyEntity), null, resolvedContext);
    }

    private IntentOutcome handleGetOncallNow(
            InterpretationParameters params, AuthenticatedSubject subject, SessionResolutionContext ctx) {
        CanonicalizationResult specialty = canonicalizationService.resolveSpecialty(params.specialtyText());
        if (specialty.status() == CanonicalizationStatus.NOT_FOUND) {
            return new IntentOutcome.NoMatch();
        }

        CanonicalizationResult location = resolveLocationWithContext(params.locationText(), specialty.canonicalId(), ctx);
        IntentOutcome locationOutcome = ambiguousOrNotFoundLocationOutcome(location);
        if (locationOutcome != null) {
            return locationOutcome;
        }

        CanonicalizationResult roleResult = resolveRoleWithContext(params.roleText(), ctx);
        if (roleResult != null && roleResult.status() == CanonicalizationStatus.NOT_FOUND) {
            return new IntentOutcome.NoMatch();
        }
        String roleCode = roleResult == null ? null : roleResult.canonicalId();

        Location locationEntity = locationRepository.findById(location.canonicalId()).orElseThrow();
        Specialty specialtyEntity = specialtyRepository.findById(specialty.canonicalId()).orElseThrow();
        ResolvedQueryContext resolvedContext = new ResolvedQueryContext(
                locationEntity.getDisplayName(), specialtyEntity.getDisplayName(), roleDisplayName(roleCode));

        ToolResult<CoverageAssignment> result = onCallToolService.getOnCallNow(
                subject, location.canonicalId(), specialty.canonicalId(), roleCode, clock.instant());
        if (result.status() == ToolResultStatus.NO_MATCH) {
            return new IntentOutcome.NoMatch(resolvedContext);
        }
        if (result.status() == ToolResultStatus.AMBIGUOUS) {
            return ambiguousOnCallRoleClarification(result.ambiguousCandidateIds());
        }

        CoverageAssignment assignment = result.data();
        Provider provider = providerRepository.findById(assignment.getProviderId()).orElseThrow();
        ContactMethod primaryContact = findPagerContact(subject, provider.getId());

        String text = formatter.formatOnCallNow(
                provider, specialtyEntity, locationEntity, assignment,
                ZoneId.of(locationEntity.getTimeZone()), primaryContact);
        return new IntentOutcome.Answer(
                text, new LastResultContext(provider.getId(), provider.getDisplayName()), resolvedContext);
    }

    private IntentOutcome handleGetOncallSchedule(
            InterpretationParameters params, AuthenticatedSubject subject, SessionResolutionContext ctx) {
        CanonicalizationResult specialty = canonicalizationService.resolveSpecialty(params.specialtyText());
        if (specialty.status() == CanonicalizationStatus.NOT_FOUND) {
            return new IntentOutcome.NoMatch();
        }

        CanonicalizationResult location = resolveLocationWithContext(params.locationText(), specialty.canonicalId(), ctx);
        IntentOutcome locationOutcome = ambiguousOrNotFoundLocationOutcome(location);
        if (locationOutcome != null) {
            return locationOutcome;
        }

        CanonicalizationResult roleResult = resolveRoleWithContext(params.roleText(), ctx);
        if (roleResult != null && roleResult.status() == CanonicalizationStatus.NOT_FOUND) {
            return new IntentOutcome.NoMatch();
        }
        String roleCode = roleResult == null ? null : roleResult.canonicalId();

        Location locationEntity = locationRepository.findById(location.canonicalId()).orElseThrow();
        Specialty specialtyEntity = specialtyRepository.findById(specialty.canonicalId()).orElseThrow();
        ResolvedQueryContext resolvedContext = new ResolvedQueryContext(
                locationEntity.getDisplayName(), specialtyEntity.getDisplayName(), roleDisplayName(roleCode));

        TimeInterval interval;
        try {
            interval = timeIntervalResolver.resolve(
                    params.timeExpression().kind(),
                    params.timeExpression().specificDate(),
                    ZoneId.of(locationEntity.getTimeZone()));
        } catch (IllegalArgumentException e) {
            // e.g. an unsupported kind for this intent (never legitimately CURRENT here per
            // config/intents.yaml time_policy.allowed_kinds) — fail closed, never guess.
            return new IntentOutcome.NoMatch(resolvedContext);
        }

        ToolResult<List<CoverageAssignment>> result = onCallToolService.getOnCallSchedule(
                subject, location.canonicalId(), specialty.canonicalId(), roleCode, interval.startAt(), interval.endAt());
        if (result.status() == ToolResultStatus.NO_MATCH) {
            return new IntentOutcome.NoMatch(resolvedContext);
        }

        List<CoverageAssignment> assignments = result.data();
        Set<String> providerIds = new LinkedHashSet<>();
        Set<String> roleCodes = new LinkedHashSet<>();
        for (CoverageAssignment assignment : assignments) {
            providerIds.add(assignment.getProviderId());
            roleCodes.add(assignment.getRoleCode());
        }
        List<Provider> providers = providerRepository.findAllById(providerIds);
        List<OnCallRole> roles = onCallRoleRepository.findAllById(roleCodes);

        String text = formatter.formatOnCallSchedule(
                specialtyEntity, locationEntity, assignments, providers, roles, ZoneId.of(locationEntity.getTimeZone()));

        LastResultContext newLastResult = providerIds.size() == 1
                ? new LastResultContext(
                        providerIds.iterator().next(),
                        providers.isEmpty() ? null : providers.get(0).getDisplayName())
                : new LastResultContext(null, null);
        return new IntentOutcome.Answer(text, newLastResult, resolvedContext);
    }

    private IntentOutcome handleGetContactInfo(
            InterpretationParameters params, AuthenticatedSubject subject, SessionResolutionContext ctx) {
        String providerId;
        if (params.providerReference().kind() == ProviderReferenceKind.EXPLICIT_TEXT) {
            CanonicalizationResult provider = canonicalizationService.resolveProvider(params.providerReference().text());
            if (provider.status() == CanonicalizationStatus.NOT_FOUND) {
                return new IntentOutcome.NoMatch();
            }
            if (provider.status() == CanonicalizationStatus.AMBIGUOUS) {
                List<Provider> candidates = providerRepository.findAllById(provider.candidateIds());
                List<ClarificationOption> options = candidates.stream()
                        .map(p -> new ClarificationOption(p.getId(), p.getDisplayName()))
                        .toList();
                return new IntentOutcome.Clarify(
                        ClarificationReason.AMBIGUOUS_ENTITY, ClarificationParameterName.PROVIDER_REFERENCE, options);
            }
            providerId = provider.canonicalId();
        } else {
            // LAST_RESULT_PROVIDER: Python only returns this kind when the safe session
            // context it was given actually stated a single prior provider (Phase 2
            // prompt contract) — Spring still never trusts that as the canonical ID itself,
            // it re-derives it from its own trusted session state (FR-013).
            LastResultContext sessionLastResult = ctx.lastResult();
            if (sessionLastResult == null || sessionLastResult.singleProviderId() == null) {
                return new IntentOutcome.Clarify(
                        ClarificationReason.MISSING_PARAMETER, ClarificationParameterName.PROVIDER_REFERENCE, List.of());
            }
            providerId = sessionLastResult.singleProviderId();
        }

        ToolResult<List<ContactMethod>> result = contactInfoToolService.getContactInfo(subject, providerId);
        if (result.status() == ToolResultStatus.NO_MATCH) {
            return new IntentOutcome.NoMatch();
        }
        Provider provider = providerRepository.findById(providerId).orElseThrow();
        String requestedType = params.contactType() != null ? params.contactType().name() : null;
        String text = formatter.formatContactInfo(provider, result.data(), requestedType);
        return new IntentOutcome.Answer(text, new LastResultContext(provider.getId(), provider.getDisplayName()));
    }

    private IntentOutcome handleConsultRouting(InterpretationParameters params, AuthenticatedSubject subject) {
        CanonicalizationResult location = canonicalizationService.resolveLocation(params.locationText());
        if (location.status() == CanonicalizationStatus.NOT_FOUND) {
            return new IntentOutcome.NoMatch();
        }
        CanonicalizationResult specialty = canonicalizationService.resolveSpecialty(params.specialtyText());
        if (specialty.status() == CanonicalizationStatus.NOT_FOUND) {
            return new IntentOutcome.NoMatch();
        }

        Location locationEntity = locationRepository.findById(location.canonicalId()).orElseThrow();
        Specialty specialtyEntity = specialtyRepository.findById(specialty.canonicalId()).orElseThrow();
        ResolvedQueryContext resolvedContext =
                new ResolvedQueryContext(locationEntity.getDisplayName(), specialtyEntity.getDisplayName(), null);

        ToolResult<List<ConsultRoutingRule>> result = consultRoutingToolService.getConsultRouting(
                subject, location.canonicalId(), specialty.canonicalId(), params.timeContext(), params.declaredUrgency());
        if (result.status() == ToolResultStatus.NO_MATCH) {
            return new IntentOutcome.NoMatch(resolvedContext);
        }
        return new IntentOutcome.Answer(
                formatter.formatConsultRouting(locationEntity, specialtyEntity, result.data()), null, resolvedContext);
    }

    private IntentOutcome handleChartChatGuidance(InterpretationParameters params, AuthenticatedSubject subject) {
        CanonicalizationResult location = canonicalizationService.resolveLocation(params.locationText());
        if (location.status() == CanonicalizationStatus.NOT_FOUND) {
            return new IntentOutcome.NoMatch();
        }
        CanonicalizationResult specialty = canonicalizationService.resolveSpecialty(params.specialtyText());
        if (specialty.status() == CanonicalizationStatus.NOT_FOUND) {
            return new IntentOutcome.NoMatch();
        }

        Location locationEntity = locationRepository.findById(location.canonicalId()).orElseThrow();
        Specialty specialtyEntity = specialtyRepository.findById(specialty.canonicalId()).orElseThrow();
        ResolvedQueryContext resolvedContext =
                new ResolvedQueryContext(locationEntity.getDisplayName(), specialtyEntity.getDisplayName(), null);

        ToolResult<List<ConsultRoutingRule>> result = consultRoutingToolService.getChartChatGuidance(
                subject, location.canonicalId(), specialty.canonicalId(), params.timeContext());
        if (result.status() == ToolResultStatus.NO_MATCH) {
            return new IntentOutcome.NoMatch(resolvedContext);
        }
        return new IntentOutcome.Answer(
                formatter.formatChartChatGuidance(locationEntity, specialtyEntity, result.data()), null, resolvedContext);
    }

    private IntentOutcome handleRoleExplanation(InterpretationParameters params, AuthenticatedSubject subject) {
        CanonicalizationResult role = canonicalizationService.resolveRole(params.roleText());
        if (role.status() == CanonicalizationStatus.NOT_FOUND) {
            return new IntentOutcome.NoMatch();
        }
        ToolResult<OnCallRole> result = roleExplanationToolService.getRoleExplanation(subject, role.canonicalId());
        if (result.status() == ToolResultStatus.NO_MATCH) {
            return new IntentOutcome.NoMatch();
        }
        return new IntentOutcome.Answer(
                formatter.formatRoleExplanation(result.data()),
                null,
                new ResolvedQueryContext(null, null, result.data().getDisplayName()));
    }

    /**
     * BACKEND_UNIQUE_OR_CLARIFY location resolution, generalized for Phase 4:
     * a resumed {@code SELECTED_OPTION} reply passes its canonical id
     * straight through (see class Javadoc "Trusted selections"); otherwise
     * an omitted, still-ambiguous location falls back to the session's last
     * resolved location when it remains a valid candidate for this
     * specialty (FR-013 "Same location.").
     */
    private CanonicalizationResult resolveLocationWithContext(
            String locationText, String specialtyCanonicalId, SessionResolutionContext ctx) {
        if (ctx.isTrustedSelectionFor(ClarificationParameterName.LOCATION_TEXT)) {
            return CanonicalizationResult.matched(ctx.trustedSelectionCanonicalId());
        }
        CanonicalizationResult location =
                canonicalizationService.resolveLocationForSpecialty(locationText, specialtyCanonicalId);
        if (location.status() == CanonicalizationStatus.AMBIGUOUS && (locationText == null || locationText.isBlank())) {
            return applyContextLocationFallback(location, ctx.lastQuery(), specialtyCanonicalId);
        }
        return location;
    }

    private CanonicalizationResult applyContextLocationFallback(
            CanonicalizationResult ambiguous, LastQueryContext lastQuery, String specialtyCanonicalId) {
        if (lastQuery == null || lastQuery.locationText() == null) {
            return ambiguous;
        }
        CanonicalizationResult fromContext = canonicalizationService.resolveLocation(lastQuery.locationText());
        if (fromContext.status() == CanonicalizationStatus.MATCHED
                && ambiguous.candidateIds().contains(fromContext.canonicalId())) {
            return fromContext;
        }
        return ambiguous;
    }

    /** Null means "no role filter requested" (role_text is always optional here). */
    private CanonicalizationResult resolveRoleWithContext(String roleText, SessionResolutionContext ctx) {
        if (ctx.isTrustedSelectionFor(ClarificationParameterName.ROLE_TEXT)) {
            return CanonicalizationResult.matched(ctx.trustedSelectionCanonicalId());
        }
        if (roleText == null || roleText.isBlank()) {
            return null;
        }
        return canonicalizationService.resolveRole(roleText);
    }

    private String roleDisplayName(String roleCode) {
        if (roleCode == null) {
            return null;
        }
        return onCallRoleRepository.findById(roleCode).map(OnCallRole::getDisplayName).orElse(null);
    }

    /** Null return means "proceed": location resolved to exactly one MATCHED candidate. */
    private IntentOutcome ambiguousOrNotFoundLocationOutcome(CanonicalizationResult location) {
        if (location.status() == CanonicalizationStatus.NOT_FOUND) {
            return new IntentOutcome.NoMatch();
        }
        if (location.status() == CanonicalizationStatus.AMBIGUOUS) {
            List<Location> candidates = locationRepository.findAllById(location.candidateIds());
            List<ClarificationOption> options = candidates.stream()
                    .map(l -> new ClarificationOption(l.getId(), l.getDisplayName()))
                    .toList();
            return new IntentOutcome.Clarify(
                    ClarificationReason.AMBIGUOUS_ENTITY, ClarificationParameterName.LOCATION_TEXT, options);
        }
        return null;
    }

    private IntentOutcome ambiguousOnCallRoleClarification(List<String> ambiguousAssignmentIds) {
        List<CoverageAssignment> assignments = coverageAssignmentRepository.findAllById(ambiguousAssignmentIds);
        Set<String> roleCodes = new LinkedHashSet<>();
        for (CoverageAssignment assignment : assignments) {
            roleCodes.add(assignment.getRoleCode());
        }
        List<OnCallRole> roles = onCallRoleRepository.findAllById(roleCodes);
        List<ClarificationOption> options = roles.stream()
                .map(r -> new ClarificationOption(r.getCode(), r.getDisplayName()))
                .toList();
        return new IntentOutcome.Clarify(
                ClarificationReason.AMBIGUOUS_RESULT, ClarificationParameterName.ROLE_TEXT, options);
    }

    private ContactMethod findPagerContact(AuthenticatedSubject subject, String providerId) {
        ToolResult<List<ContactMethod>> contacts = contactInfoToolService.getContactInfo(subject, providerId);
        if (contacts.status() != ToolResultStatus.FOUND) {
            return null;
        }
        return contacts.data().stream()
                .filter(c -> c.getContactType() == ContactType.PAGER)
                .findFirst()
                .orElse(null);
    }
}
