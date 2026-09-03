package com.clinconnect.chatbot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Phase 7 POC hardening: codifies docs/03-ARCHITECTURE.md's "Required Abstraction Boundaries"
 * and docs/02-REQUIREMENTS.md NFR-002 ("preserve interfaces allowing H2 -> PostgreSQL/source
 * adapter and in-memory conversation state -> Redis without changing business/API contracts")
 * as regression-checked source scans, rather than a one-time manual audit note — a future
 * change that quietly reintroduces H2-specific SQL or leaks the in-memory session store's
 * concrete type outside its own package will fail this test immediately.
 *
 * <p>Per PLAN.md Phase 7 ("Verify H2 -> PostgreSQL/source-adapter and in-memory -> Redis
 * replacement seams. Do not implement PostgreSQL/Redis unless explicitly requested"), this is
 * verification only — it asserts nothing about, and requires, an actual PostgreSQL or Redis
 * instance.
 */
class ReplacementSeamTest {

    private static final Path MAIN_SOURCE_ROOT = Path.of("src/main/java");
    private static final Path SESSION_PACKAGE_ROOT =
            Path.of("src/main/java/com/clinconnect/chatbot/session");

    /**
     * H2 -> PostgreSQL/source-adapter seam: every {@code @Query} in this codebase must be
     * portable JPQL, never {@code nativeQuery = true} H2-specific SQL — Spring Data JPA's
     * derived-query methods and JPQL translate to PostgreSQL unchanged; native SQL would not.
     */
    @Test
    void noRepositoryQueryUsesNativeSql() throws IOException {
        List<Path> offenders = javaFilesUnder(MAIN_SOURCE_ROOT)
                .filter(path -> containsNativeQueryTrue(readFile(path)))
                .toList();

        assertThat(offenders)
                .as("nativeQuery = true ties a repository to H2-specific SQL, breaking the "
                        + "PostgreSQL/source-adapter replacement seam (docs/03-ARCHITECTURE.md)")
                .isEmpty();
    }

    /**
     * In-memory -> Redis conversation-state seam: nothing outside the {@code session} package
     * itself may reference the concrete {@code InMemoryConversationSessionStore} type or
     * {@code ConcurrentHashMap} — every other class must depend only on the {@code
     * ConversationSessionStore} interface (docs/07-DATA-MODEL.md "Replacement Requirement":
     * "Conversation-state contracts must allow Redis later without external contract changes").
     */
    @Test
    void noClassOutsideSessionPackageReferencesTheConcreteInMemoryStore() throws IOException {
        List<Path> offenders = javaFilesUnder(MAIN_SOURCE_ROOT)
                .filter(path -> !path.startsWith(SESSION_PACKAGE_ROOT))
                .filter(path -> {
                    String content = readFile(path);
                    return content.contains("InMemoryConversationSessionStore") || content.contains("ConcurrentHashMap");
                })
                .toList();

        assertThat(offenders)
                .as("Only com.clinconnect.chatbot.session may reference the concrete in-memory "
                        + "store/map types — everything else must depend on ConversationSessionStore "
                        + "so it can be replaced by Redis without touching callers (docs/07-DATA-MODEL.md)")
                .isEmpty();
    }

    private static Stream<Path> javaFilesUnder(Path root) throws IOException {
        return Files.walk(root).filter(p -> p.toString().endsWith(".java"));
    }

    private static boolean containsNativeQueryTrue(String content) {
        return content.replaceAll("\\s+", "").contains("nativeQuery=true");
    }

    private static String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
