package com.clinconnect.chatbot.canonicalization;

import com.clinconnect.chatbot.config.ChatbotConfigLoader;
import com.clinconnect.chatbot.domain.model.Location;
import com.clinconnect.chatbot.domain.model.OnCallRole;
import com.clinconnect.chatbot.domain.model.Provider;
import com.clinconnect.chatbot.domain.model.Specialty;
import com.clinconnect.chatbot.domain.repository.LocationRepository;
import com.clinconnect.chatbot.domain.repository.LocationSpecialtyRepository;
import com.clinconnect.chatbot.domain.repository.OnCallRoleRepository;
import com.clinconnect.chatbot.domain.repository.ProviderRepository;
import com.clinconnect.chatbot.domain.repository.SpecialtyRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Resolves free-text interpretation parameters to canonical persisted IDs.
 * The model never produces canonical IDs (docs/09-SECURITY.md); this is the
 * only place that does. Exact/alias matching only (FR-015) — no fuzzy
 * matching, so a typo that misses an approved alias falls through to
 * NOT_FOUND rather than a guessed ID.
 */
@Service
public class CanonicalizationService {

    private final LocationRepository locationRepository;
    private final SpecialtyRepository specialtyRepository;
    private final LocationSpecialtyRepository locationSpecialtyRepository;
    private final ProviderRepository providerRepository;
    private final OnCallRoleRepository onCallRoleRepository;
    private final ChatbotConfigLoader chatbotConfigLoader;

    public CanonicalizationService(
            LocationRepository locationRepository,
            SpecialtyRepository specialtyRepository,
            LocationSpecialtyRepository locationSpecialtyRepository,
            ProviderRepository providerRepository,
            OnCallRoleRepository onCallRoleRepository,
            ChatbotConfigLoader chatbotConfigLoader) {
        this.locationRepository = locationRepository;
        this.specialtyRepository = specialtyRepository;
        this.locationSpecialtyRepository = locationSpecialtyRepository;
        this.providerRepository = providerRepository;
        this.onCallRoleRepository = onCallRoleRepository;
        this.chatbotConfigLoader = chatbotConfigLoader;
    }

    public CanonicalizationResult resolveLocation(String locationText) {
        if (locationText == null || locationText.isBlank()) {
            return CanonicalizationResult.notFound();
        }
        String normalized = normalize(locationText);
        Optional<Location> bySlug = locationRepository.findBySlugIgnoreCaseAndActiveTrue(normalized);
        if (bySlug.isPresent()) {
            return CanonicalizationResult.matched(bySlug.get().getId());
        }
        Optional<Location> byName = locationRepository.findByDisplayNameIgnoreCaseAndActiveTrue(locationText.trim());
        return byName.map(l -> CanonicalizationResult.matched(l.getId()))
                .orElseGet(CanonicalizationResult::notFound);
    }

    public CanonicalizationResult resolveSpecialty(String specialtyText) {
        if (specialtyText == null || specialtyText.isBlank()) {
            return CanonicalizationResult.notFound();
        }
        String normalized = normalize(specialtyText);
        String aliased = chatbotConfigLoader.config().specialtyAliases().getOrDefault(normalized, normalized);
        Optional<Specialty> bySlug = specialtyRepository.findBySlugIgnoreCaseAndActiveTrue(aliased);
        if (bySlug.isPresent()) {
            return CanonicalizationResult.matched(bySlug.get().getId());
        }
        Optional<Specialty> byName =
                specialtyRepository.findByDisplayNameIgnoreCaseAndActiveTrue(specialtyText.trim());
        return byName.map(s -> CanonicalizationResult.matched(s.getId()))
                .orElseGet(CanonicalizationResult::notFound);
    }

    public CanonicalizationResult resolveRole(String roleText) {
        if (roleText == null || roleText.isBlank()) {
            return CanonicalizationResult.notFound();
        }
        String trimmed = roleText.trim();
        Optional<OnCallRole> byCode = onCallRoleRepository.findByCodeIgnoreCaseAndActiveTrue(trimmed);
        if (byCode.isPresent()) {
            return CanonicalizationResult.matched(byCode.get().getCode());
        }
        Optional<OnCallRole> byName = onCallRoleRepository.findByDisplayNameIgnoreCaseAndActiveTrue(trimmed);
        return byName.map(r -> CanonicalizationResult.matched(r.getCode()))
                .orElseGet(CanonicalizationResult::notFound);
    }

    public CanonicalizationResult resolveProvider(String providerText) {
        if (providerText == null || providerText.isBlank()) {
            return CanonicalizationResult.notFound();
        }
        List<Provider> matches = providerRepository.findByDisplayNameIgnoreCaseAndActiveTrue(providerText.trim());
        if (matches.isEmpty()) {
            return CanonicalizationResult.notFound();
        }
        if (matches.size() == 1) {
            return CanonicalizationResult.matched(matches.get(0).getId());
        }
        return CanonicalizationResult.ambiguous(matches.stream().map(Provider::getId).toList());
    }

    /**
     * FR-004/FR-005 BACKEND_UNIQUE_OR_CLARIFY location policy: an explicit
     * location resolves normally; an omitted location auto-resolves only if
     * exactly one active location offers the canonical specialty.
     */
    public CanonicalizationResult resolveLocationForSpecialty(String locationText, String canonicalSpecialtyId) {
        if (locationText != null && !locationText.isBlank()) {
            return resolveLocation(locationText);
        }
        List<String> candidates = locationSpecialtyRepository.findActiveLocationIdsOfferingSpecialty(canonicalSpecialtyId);
        if (candidates.isEmpty()) {
            return CanonicalizationResult.notFound();
        }
        if (candidates.size() == 1) {
            return CanonicalizationResult.matched(candidates.get(0));
        }
        return CanonicalizationResult.ambiguous(candidates);
    }

    private String normalize(String text) {
        return text.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
