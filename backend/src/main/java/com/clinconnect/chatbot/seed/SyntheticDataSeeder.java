package com.clinconnect.chatbot.seed;

import com.clinconnect.chatbot.domain.model.ConsultRoutingCategory;
import com.clinconnect.chatbot.domain.model.ConsultRoutingRule;
import com.clinconnect.chatbot.domain.model.ContactMethod;
import com.clinconnect.chatbot.domain.model.ContactType;
import com.clinconnect.chatbot.domain.model.CoverageAssignment;
import com.clinconnect.chatbot.domain.model.Department;
import com.clinconnect.chatbot.domain.model.DeclaredUrgency;
import com.clinconnect.chatbot.domain.model.Location;
import com.clinconnect.chatbot.domain.model.LocationSpecialty;
import com.clinconnect.chatbot.domain.model.OnCallRole;
import com.clinconnect.chatbot.domain.model.Provider;
import com.clinconnect.chatbot.domain.model.ProviderSpecialty;
import com.clinconnect.chatbot.domain.model.Specialty;
import com.clinconnect.chatbot.domain.model.TimeContext;
import com.clinconnect.chatbot.domain.repository.ConsultRoutingRuleRepository;
import com.clinconnect.chatbot.domain.repository.ContactMethodRepository;
import com.clinconnect.chatbot.domain.repository.CoverageAssignmentRepository;
import com.clinconnect.chatbot.domain.repository.DepartmentRepository;
import com.clinconnect.chatbot.domain.repository.LocationRepository;
import com.clinconnect.chatbot.domain.repository.LocationSpecialtyRepository;
import com.clinconnect.chatbot.domain.repository.OnCallRoleRepository;
import com.clinconnect.chatbot.domain.repository.ProviderRepository;
import com.clinconnect.chatbot.domain.repository.ProviderSpecialtyRepository;
import com.clinconnect.chatbot.domain.repository.SpecialtyRepository;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Populates synthetic, fictional POC reference data on every startup
 * (NFR-007; CLAUDE.md "Data": no real clinician data or PHI) by reading the
 * hand-editable files in {@code config/seed-data/*.csv} — one file per
 * entity, column-name-addressed so row order/extra whitespace don't matter.
 * Editing the CSVs and restarting the backend is the entire workflow for
 * adding/changing seed data; no Java changes are needed. IDs match
 * docs/10-EVALUATION-PLAN.md "Planned Synthetic Fixture IDs" where
 * specified there.
 *
 * <p>Deterministic {@code coverage_assignment} test fixtures are still not
 * loaded from here: docs/10-EVALUATION-PLAN.md requires those to be created
 * relative to an injectable/fixed test clock, so tests insert their own
 * directly via {@link CoverageAssignmentRepository}. {@code
 * config/seed-data/coverage_assignments.csv} instead seeds a separate,
 * wall-clock-relative set of coverage rows purely so a live manual/demo
 * session (running outside the test suite) has something for "who is on
 * call" questions to find — each row's window is given as {@code
 * starts_offset_hours}/{@code ends_offset_hours} relative to "now" (not
 * literal timestamps), so it stays valid regardless of what time the app
 * happens to be started.
 *
 * <p>That live-demo coverage is opt-in via {@code clinconnect.seed.live-
 * demo-coverage} ({@code SEED_LIVE_DEMO_COVERAGE} in {@code .env}, default
 * {@code false}) specifically so it never runs during {@code mvn test}
 * (Maven does not source {@code .env} into the JVM environment) — every
 * deterministic test that asserts an exact match/AMBIGUOUS/NO_MATCH count
 * against its own inserted {@link CoverageAssignment} rows would otherwise
 * silently collide with these.
 *
 * <p>Runs after every context refresh because {@code ddl-auto: create}
 * recreates the schema fresh each startup (see application.yml) — this
 * keeps the POC deterministic without a migration tool.
 */
@Component
public class SyntheticDataSeeder implements ApplicationRunner {

    private final LocationRepository locationRepository;
    private final SpecialtyRepository specialtyRepository;
    private final DepartmentRepository departmentRepository;
    private final LocationSpecialtyRepository locationSpecialtyRepository;
    private final ProviderRepository providerRepository;
    private final ProviderSpecialtyRepository providerSpecialtyRepository;
    private final ContactMethodRepository contactMethodRepository;
    private final OnCallRoleRepository onCallRoleRepository;
    private final ConsultRoutingRuleRepository consultRoutingRuleRepository;
    private final CoverageAssignmentRepository coverageAssignmentRepository;
    private final Clock clock;
    private final boolean seedLiveDemoCoverage;
    private final Path seedDataDir;

    public SyntheticDataSeeder(
            LocationRepository locationRepository,
            SpecialtyRepository specialtyRepository,
            DepartmentRepository departmentRepository,
            LocationSpecialtyRepository locationSpecialtyRepository,
            ProviderRepository providerRepository,
            ProviderSpecialtyRepository providerSpecialtyRepository,
            ContactMethodRepository contactMethodRepository,
            OnCallRoleRepository onCallRoleRepository,
            ConsultRoutingRuleRepository consultRoutingRuleRepository,
            CoverageAssignmentRepository coverageAssignmentRepository,
            Clock clock,
            @Value("${clinconnect.seed.live-demo-coverage:false}") boolean seedLiveDemoCoverage,
            @Value("${clinconnect.seed.data-dir:../config/seed-data}") String seedDataDir) {
        this.locationRepository = locationRepository;
        this.specialtyRepository = specialtyRepository;
        this.departmentRepository = departmentRepository;
        this.locationSpecialtyRepository = locationSpecialtyRepository;
        this.providerRepository = providerRepository;
        this.providerSpecialtyRepository = providerSpecialtyRepository;
        this.contactMethodRepository = contactMethodRepository;
        this.onCallRoleRepository = onCallRoleRepository;
        this.consultRoutingRuleRepository = consultRoutingRuleRepository;
        this.coverageAssignmentRepository = coverageAssignmentRepository;
        this.clock = clock;
        this.seedLiveDemoCoverage = seedLiveDemoCoverage;
        this.seedDataDir = Path.of(seedDataDir);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!locationRepository.findAll().isEmpty()) {
            return;
        }

        locationRepository.saveAll(loadRows("locations.csv", (row, path) -> new Location(
                req(row, "id", path), req(row, "slug", path), req(row, "display_name", path),
                req(row, "time_zone", path), bool(row, "active", path))));

        specialtyRepository.saveAll(loadRows("specialties.csv", (row, path) -> new Specialty(
                req(row, "id", path), req(row, "slug", path), req(row, "display_name", path),
                bool(row, "active", path))));

        departmentRepository.saveAll(loadRows("departments.csv", (row, path) -> new Department(
                req(row, "id", path), req(row, "display_name", path), req(row, "location_id", path),
                req(row, "specialty_id", path), opt(row, "note"), bool(row, "active", path))));

        // location_specialties.csv is what makes a specialty "offered" at a location — it
        // drives get_specialties and the BACKEND_UNIQUE_OR_CLARIFY location candidate set for
        // get_oncall_now/get_oncall_schedule (FR-004/FR-005).
        locationSpecialtyRepository.saveAll(loadRows("location_specialties.csv", (row, path) -> new LocationSpecialty(
                req(row, "location_id", path), req(row, "specialty_id", path), bool(row, "active", path))));

        providerRepository.saveAll(loadRows("providers.csv", (row, path) -> new Provider(
                req(row, "id", path), req(row, "display_name", path), bool(row, "active", path))));

        providerSpecialtyRepository.saveAll(loadRows("provider_specialties.csv", (row, path) -> new ProviderSpecialty(
                req(row, "provider_id", path), req(row, "specialty_id", path))));

        contactMethodRepository.saveAll(loadRows("contact_methods.csv", (row, path) -> new ContactMethod(
                req(row, "id", path), req(row, "provider_id", path),
                enumReq(ContactType.class, row, "contact_type", path), req(row, "value", path),
                bool(row, "active", path))));

        onCallRoleRepository.saveAll(loadRows("on_call_roles.csv", (row, path) -> new OnCallRole(
                req(row, "code", path), req(row, "display_name", path), req(row, "definition_text", path),
                bool(row, "active", path))));

        consultRoutingRuleRepository.saveAll(loadRows("consult_routing_rules.csv", (row, path) -> new ConsultRoutingRule(
                req(row, "id", path), req(row, "location_id", path), req(row, "specialty_id", path),
                enumReq(ConsultRoutingCategory.class, row, "category", path),
                enumOpt(TimeContext.class, row, "time_context", path),
                enumOpt(DeclaredUrgency.class, row, "declared_urgency", path),
                req(row, "routing_text", path), bool(row, "active", path))));

        if (seedLiveDemoCoverage) {
            seedLiveCoverageForManualDemo();
        }
    }

    /**
     * Wall-clock-relative "who is on call" coverage for a live/manual session — see class
     * Javadoc. Loaded from config/seed-data/coverage_assignments.csv; each row's window is an
     * hour offset from "now" so it stays valid no matter when the app is started.
     */
    private void seedLiveCoverageForManualDemo() {
        Instant now = clock.instant();
        coverageAssignmentRepository.saveAll(loadRows("coverage_assignments.csv", (row, path) -> new CoverageAssignment(
                req(row, "id", path), req(row, "provider_id", path), req(row, "specialty_id", path),
                req(row, "location_id", path), req(row, "role_code", path),
                now.plusSeconds(longReq(row, "starts_offset_hours", path) * 3600),
                now.plusSeconds(longReq(row, "ends_offset_hours", path) * 3600))));
    }

    private <T> List<T> loadRows(String filename, BiFunction<Map<String, String>, Path, T> mapper) {
        Path path = seedDataDir.resolve(filename);
        List<Map<String, String>> rows = CsvReader.read(path);
        List<T> result = new ArrayList<>(rows.size());
        for (Map<String, String> row : rows) {
            result.add(mapper.apply(row, path));
        }
        return result;
    }

    private static String req(Map<String, String> row, String column, Path source) {
        String value = row.get(column);
        if (value == null || value.trim().isEmpty()) {
            throw new SeedDataException("Column '" + column + "' is required but missing/empty in " + source + ": " + row);
        }
        return value.trim();
    }

    private static String opt(Map<String, String> row, String column) {
        String value = row.get(column);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static boolean bool(Map<String, String> row, String column, Path source) {
        String value = req(row, column, source);
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        throw new SeedDataException("Column '" + column + "' must be true/false, got '" + value + "' in " + source);
    }

    private static long longReq(Map<String, String> row, String column, Path source) {
        String value = req(row, column, source);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new SeedDataException("Column '" + column + "' must be an integer, got '" + value + "' in " + source);
        }
    }

    private static <E extends Enum<E>> E enumReq(Class<E> type, Map<String, String> row, String column, Path source) {
        String value = req(row, column, source);
        return parseEnum(type, value, column, source);
    }

    private static <E extends Enum<E>> E enumOpt(Class<E> type, Map<String, String> row, String column, Path source) {
        String value = opt(row, column);
        return value == null ? null : parseEnum(type, value, column, source);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String column, Path source) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new SeedDataException(
                    "Column '" + column + "' has unknown " + type.getSimpleName() + " value '" + value + "' in "
                            + source);
        }
    }
}
