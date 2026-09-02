package com.clinconnect.chatbot.format;

import com.clinconnect.chatbot.domain.model.ConsultRoutingRule;
import com.clinconnect.chatbot.domain.model.ContactMethod;
import com.clinconnect.chatbot.domain.model.CoverageAssignment;
import com.clinconnect.chatbot.domain.model.Department;
import com.clinconnect.chatbot.domain.model.Location;
import com.clinconnect.chatbot.domain.model.OnCallRole;
import com.clinconnect.chatbot.domain.model.Provider;
import com.clinconnect.chatbot.domain.model.Specialty;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Deterministically formats tool results into the baseline factual answer
 * text (docs/06-CONVERSATION-DESIGN.md). Every word here comes from
 * persisted/config data; nothing is model-generated (NFR-004/NFR-006).
 */
@Component
public class FactualResponseFormatter {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    public String formatLocations(List<Location> locations) {
        if (locations.isEmpty()) {
            return "No active locations are available.";
        }
        return "Locations: " + joinDisplayNames(locations.stream().map(Location::getDisplayName).toList());
    }

    public String formatSpecialties(Location location, List<Specialty> specialties) {
        if (specialties.isEmpty()) {
            return "No active specialties are listed for " + location.getDisplayName() + ".";
        }
        return "Specialties at " + location.getDisplayName() + ": "
                + joinDisplayNames(specialties.stream().map(Specialty::getDisplayName).toList());
    }

    public String formatDepartmentInfo(Department department, Location location, Specialty specialty) {
        StringBuilder text = new StringBuilder();
        text.append(department.getDisplayName())
                .append(" (").append(location.getDisplayName())
                .append(" — ").append(specialty.getDisplayName()).append(").");
        if (department.getNote() != null && !department.getNote().isBlank()) {
            text.append("\n\n").append(department.getNote());
        }
        return text.toString();
    }

    public String formatOnCallNow(
            Provider provider,
            Specialty specialty,
            Location location,
            CoverageAssignment assignment,
            ZoneId locationZone,
            ContactMethod primaryContact) {
        String endTime = TIME_FORMAT.format(assignment.getEndsAt().atZone(locationZone));
        String text = "%s is on call for %s in %s until %s.".formatted(
                provider.getDisplayName(), specialty.getDisplayName(), location.getDisplayName(), endTime);
        if (primaryContact != null) {
            text += "\n\n" + ContactTypeLabels.label(primaryContact.getContactType()) + ": " + primaryContact.getValue();
        }
        return text;
    }

    public String formatOnCallSchedule(
            Specialty specialty,
            Location location,
            List<CoverageAssignment> assignments,
            List<Provider> providersById,
            List<OnCallRole> rolesByCode,
            ZoneId locationZone) {
        StringBuilder text = new StringBuilder();
        text.append("On-call schedule for ").append(specialty.getDisplayName())
                .append(" in ").append(location.getDisplayName()).append(":");
        for (CoverageAssignment assignment : assignments) {
            String providerName = providersById.stream()
                    .filter(p -> p.getId().equals(assignment.getProviderId()))
                    .findFirst()
                    .map(Provider::getDisplayName)
                    .orElse(assignment.getProviderId());
            String roleName = rolesByCode.stream()
                    .filter(r -> r.getCode().equals(assignment.getRoleCode()))
                    .findFirst()
                    .map(OnCallRole::getDisplayName)
                    .orElse(assignment.getRoleCode());
            String start = TIME_FORMAT.format(assignment.getStartsAt().atZone(locationZone));
            String end = TIME_FORMAT.format(assignment.getEndsAt().atZone(locationZone));
            text.append("\n- ").append(providerName).append(" (").append(roleName)
                    .append("): ").append(start).append(" to ").append(end);
        }
        return text.toString();
    }

    public String formatContactInfo(Provider provider, List<ContactMethod> contacts, String requestedTypeOrNull) {
        StringBuilder text = new StringBuilder(provider.getDisplayName()).append(" contact methods:");
        for (ContactMethod contact : contacts) {
            text.append("\n- ").append(ContactTypeLabels.label(contact.getContactType()))
                    .append(": ").append(contact.getValue());
        }
        if (requestedTypeOrNull != null
                && contacts.stream().noneMatch(c -> c.getContactType().name().equalsIgnoreCase(requestedTypeOrNull))) {
            text.append("\n\n").append(requestedTypeOrNull).append(" is not listed for ")
                    .append(provider.getDisplayName()).append(".");
        }
        return text.toString();
    }

    public String formatConsultRouting(Location location, Specialty specialty, List<ConsultRoutingRule> rules) {
        StringBuilder text = new StringBuilder("Consult routing for ")
                .append(specialty.getDisplayName()).append(" in ").append(location.getDisplayName()).append(":");
        for (ConsultRoutingRule rule : rules) {
            text.append("\n- ").append(rule.getRoutingText());
        }
        return text.toString();
    }

    public String formatChartChatGuidance(Location location, Specialty specialty, List<ConsultRoutingRule> guidance) {
        StringBuilder text = new StringBuilder("Chart Chat guidance for ")
                .append(specialty.getDisplayName()).append(" in ").append(location.getDisplayName()).append(":");
        for (ConsultRoutingRule rule : guidance) {
            text.append("\n- ").append(rule.getRoutingText());
        }
        return text.toString();
    }

    public String formatRoleExplanation(OnCallRole role) {
        return role.getDisplayName() + ": " + role.getDefinitionText();
    }

    private String joinDisplayNames(List<String> names) {
        return String.join(", ", names);
    }
}
