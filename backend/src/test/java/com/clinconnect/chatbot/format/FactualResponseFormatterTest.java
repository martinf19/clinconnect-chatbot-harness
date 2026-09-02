package com.clinconnect.chatbot.format;

import static org.assertj.core.api.Assertions.assertThat;

import com.clinconnect.chatbot.domain.model.ContactMethod;
import com.clinconnect.chatbot.domain.model.ContactType;
import com.clinconnect.chatbot.domain.model.CoverageAssignment;
import com.clinconnect.chatbot.domain.model.Location;
import com.clinconnect.chatbot.domain.model.OnCallRole;
import com.clinconnect.chatbot.domain.model.Provider;
import com.clinconnect.chatbot.domain.model.Specialty;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class FactualResponseFormatterTest {

    private final FactualResponseFormatter formatter = new FactualResponseFormatter();
    private final ZoneId oaklandZone = ZoneId.of("America/Los_Angeles");

    @Test
    void formatsOnCallNowExactlyLikeTheDocumentedBaselineExample() {
        // docs/06-CONVERSATION-DESIGN.md: "Dr. Avery Chen is on call for
        // Neurology in Oakland until 7:00 PM.\n\nPager: 555-0104"
        Provider provider = new Provider("provider-avery-chen", "Dr. Avery Chen", true);
        Specialty specialty = new Specialty("spec-neurology", "neurology", "Neurology", true);
        Location location = new Location("loc-oakland", "oakland", "Oakland", "America/Los_Angeles", true);
        CoverageAssignment assignment = new CoverageAssignment(
                "assignment-1",
                provider.getId(),
                specialty.getId(),
                location.getId(),
                "PRIMARY_ONCALL",
                Instant.parse("2026-06-15T17:00:00Z"),
                Instant.parse("2026-06-16T02:00:00Z")); // 7:00 PM PDT
        ContactMethod pager =
                new ContactMethod("contact-1", provider.getId(), ContactType.PAGER, "555-0104", true);

        String text = formatter.formatOnCallNow(provider, specialty, location, assignment, oaklandZone, pager);

        assertThat(text).isEqualTo("Dr. Avery Chen is on call for Neurology in Oakland until 7:00 PM."
                + "\n\nPager: 555-0104");
    }

    @Test
    void formatsOnCallNowWithoutAContactWhenNoneIsPassed() {
        Provider provider = new Provider("provider-avery-chen", "Dr. Avery Chen", true);
        Specialty specialty = new Specialty("spec-neurology", "neurology", "Neurology", true);
        Location location = new Location("loc-oakland", "oakland", "Oakland", "America/Los_Angeles", true);
        CoverageAssignment assignment = new CoverageAssignment(
                "assignment-1",
                provider.getId(),
                specialty.getId(),
                location.getId(),
                "PRIMARY_ONCALL",
                Instant.parse("2026-06-15T17:00:00Z"),
                Instant.parse("2026-06-16T02:00:00Z"));

        String text = formatter.formatOnCallNow(provider, specialty, location, assignment, oaklandZone, null);

        assertThat(text).isEqualTo("Dr. Avery Chen is on call for Neurology in Oakland until 7:00 PM.");
    }

    @Test
    void formatsLocationsAsACommaJoinedList() {
        Location oakland = new Location("loc-oakland", "oakland", "Oakland", "America/Los_Angeles", true);
        Location antioch = new Location("loc-antioch", "antioch", "Antioch", "America/Los_Angeles", true);

        assertThat(formatter.formatLocations(List.of(oakland, antioch))).isEqualTo("Locations: Oakland, Antioch");
    }

    @Test
    void formatsEmptyLocationsWithoutInventingAny() {
        assertThat(formatter.formatLocations(List.of())).isEqualTo("No active locations are available.");
    }

    @Test
    void formatsContactInfoAndNotesAMissingRequestedType() {
        Provider provider = new Provider("provider-jordan-lee", "Dr. Jordan Lee", true);
        ContactMethod office =
                new ContactMethod("contact-2", provider.getId(), ContactType.OFFICE, "555-0200", true);

        String text = formatter.formatContactInfo(provider, List.of(office), "PAGER");

        assertThat(text).contains("Dr. Jordan Lee contact methods:")
                .contains("- Office: 555-0200")
                .contains("PAGER is not listed for Dr. Jordan Lee.");
    }

    @Test
    void formatsRoleExplanationFromStoredDefinitionOnly() {
        OnCallRole role = new OnCallRole(
                "PRIMARY_ONCALL", "Primary On-Call", "The clinician primarily responsible for new consults.", true);

        assertThat(formatter.formatRoleExplanation(role))
                .isEqualTo("Primary On-Call: The clinician primarily responsible for new consults.");
    }
}
