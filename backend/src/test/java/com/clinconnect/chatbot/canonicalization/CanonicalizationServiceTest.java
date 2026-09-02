package com.clinconnect.chatbot.canonicalization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Exercises canonicalization against the real seeded synthetic dataset
 * (SyntheticDataSeeder) — deterministic H2 data, no Python/Ollama involved.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CanonicalizationServiceTest {

    @Autowired
    private CanonicalizationService canonicalizationService;

    @Test
    void resolvesLocationBySlugCaseInsensitively() {
        CanonicalizationResult result = canonicalizationService.resolveLocation("OAKLAND");
        assertThat(result.status()).isEqualTo(CanonicalizationStatus.MATCHED);
        assertThat(result.canonicalId()).isEqualTo("loc-oakland");
    }

    @Test
    void resolvesLocationByDisplayName() {
        CanonicalizationResult result = canonicalizationService.resolveLocation("Antioch");
        assertThat(result.status()).isEqualTo(CanonicalizationStatus.MATCHED);
        assertThat(result.canonicalId()).isEqualTo("loc-antioch");
    }

    @Test
    void unknownLocationTextIsNotFoundRatherThanGuessed() {
        CanonicalizationResult result = canonicalizationService.resolveLocation("Nowhereville");
        assertThat(result.status()).isEqualTo(CanonicalizationStatus.NOT_FOUND);
    }

    @Test
    void resolvesSpecialtyThroughAnApprovedAlias() {
        // FR-015: "cards" -> cardiology via config/intents.yaml aliases.specialty.
        CanonicalizationResult result = canonicalizationService.resolveSpecialty("cards");
        assertThat(result.status()).isEqualTo(CanonicalizationStatus.MATCHED);
        assertThat(result.canonicalId()).isEqualTo("spec-cardiology");
    }

    @Test
    void resolvesSpecialtyBySlugWhenNoAliasApplies() {
        CanonicalizationResult result = canonicalizationService.resolveSpecialty("neurology");
        assertThat(result.status()).isEqualTo(CanonicalizationStatus.MATCHED);
        assertThat(result.canonicalId()).isEqualTo("spec-neurology");
    }

    @Test
    void aTypoThatMissesAnApprovedAliasIsNotFuzzyMatched() {
        // FR-015: no fuzzy matching by default.
        CanonicalizationResult result = canonicalizationService.resolveSpecialty("cardiologyy");
        assertThat(result.status()).isEqualTo(CanonicalizationStatus.NOT_FOUND);
    }

    @Test
    void explicitLocationIsUsedDirectlyRegardlessOfSpecialty() {
        CanonicalizationResult result =
                canonicalizationService.resolveLocationForSpecialty("Oakland", "spec-cardiology");
        assertThat(result.status()).isEqualTo(CanonicalizationStatus.MATCHED);
        assertThat(result.canonicalId()).isEqualTo("loc-oakland");
    }

    @Test
    void omittedLocationAutoResolvesWhenExactlyOneLocationOffersTheSpecialty() {
        // Pediatrics is only offered at Oakland in the seeded data.
        CanonicalizationResult result =
                canonicalizationService.resolveLocationForSpecialty(null, "spec-pediatrics");
        assertThat(result.status()).isEqualTo(CanonicalizationStatus.MATCHED);
        assertThat(result.canonicalId()).isEqualTo("loc-oakland");
    }

    @Test
    void omittedLocationIsAmbiguousWhenMultipleLocationsOfferTheSpecialty() {
        // Cardiology is offered at both Oakland and Antioch in the seeded data.
        CanonicalizationResult result =
                canonicalizationService.resolveLocationForSpecialty("", "spec-cardiology");
        assertThat(result.status()).isEqualTo(CanonicalizationStatus.AMBIGUOUS);
        assertThat(result.candidateIds()).containsExactlyInAnyOrder("loc-oakland", "loc-antioch");
    }

    @Test
    void omittedLocationIsNotFoundWhenNoLocationOffersTheSpecialty() {
        CanonicalizationResult result =
                canonicalizationService.resolveLocationForSpecialty(null, "spec-does-not-exist");
        assertThat(result.status()).isEqualTo(CanonicalizationStatus.NOT_FOUND);
    }

    @Test
    void resolvesRoleByCode() {
        CanonicalizationResult result = canonicalizationService.resolveRole("primary_oncall");
        assertThat(result.status()).isEqualTo(CanonicalizationStatus.MATCHED);
        assertThat(result.canonicalId()).isEqualTo("PRIMARY_ONCALL");
    }

    @Test
    void resolvesRoleByDisplayName() {
        CanonicalizationResult result = canonicalizationService.resolveRole("Backup On-Call");
        assertThat(result.status()).isEqualTo(CanonicalizationStatus.MATCHED);
        assertThat(result.canonicalId()).isEqualTo("BACKUP_ONCALL");
    }

    @Test
    void resolvesProviderByExactDisplayName() {
        CanonicalizationResult result = canonicalizationService.resolveProvider("Dr. Avery Chen");
        assertThat(result.status()).isEqualTo(CanonicalizationStatus.MATCHED);
        assertThat(result.canonicalId()).isEqualTo("provider-avery-chen");
    }

    @Test
    void unknownProviderIsNotFound() {
        CanonicalizationResult result = canonicalizationService.resolveProvider("Dr. Nobody");
        assertThat(result.status()).isEqualTo(CanonicalizationStatus.NOT_FOUND);
    }
}
