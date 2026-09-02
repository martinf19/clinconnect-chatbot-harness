package com.clinconnect.chatbot.evaluation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads {@code tests/evaluation/*.yaml} (docs/10-EVALUATION-PLAN.md) for the deterministic
 * Layer B/C/D JUnit evaluation runners (Phase 6). Cases are addressed by {@code id}; expected
 * values and turn messages are read from the committed YAML at test-run time via {@link #path}
 * rather than duplicated as Java literals, so a case's actual message wording/expected values
 * always match what's checked into {@code tests/evaluation/} — the evaluation runners cannot
 * silently drift from that file.
 */
final class EvaluationYaml {

    private EvaluationYaml() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> loadCase(String fileName, String caseId) {
        Path resolved = Path.of("../tests/evaluation/" + fileName);
        Map<String, Object> doc;
        try (InputStream in = Files.newInputStream(resolved)) {
            doc = (Map<String, Object>) new Yaml().load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read evaluation file: " + resolved.toAbsolutePath(), e);
        }
        List<Map<String, Object>> cases = (List<Map<String, Object>>) doc.get("cases");
        return cases.stream()
                .filter(c -> caseId.equals(c.get("id")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No case '" + caseId + "' in " + fileName + " — evaluation file and test have drifted"));
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> turns(Map<String, Object> testCase) {
        return (List<Map<String, Object>>) testCase.get("turns");
    }

    /** Dotted-path lookup into a parsed YAML node, e.g. {@code path(turn, "expected.canonical.location_id")}. */
    static Object path(Map<String, Object> node, String dottedPath) {
        Object current = node;
        for (String part : dottedPath.split("\\.")) {
            if (!(current instanceof Map<?, ?> m)) {
                return null;
            }
            current = m.get(part);
        }
        return current;
    }

    static String str(Map<String, Object> node, String dottedPath) {
        Object value = path(node, dottedPath);
        return value == null ? null : value.toString();
    }

    static Boolean bool(Map<String, Object> node, String dottedPath) {
        Object value = path(node, dottedPath);
        return value == null ? null : (Boolean) value;
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Map<String, Object> node, String dottedPath) {
        Object value = path(node, dottedPath);
        return value == null ? List.of() : (List<Object>) value;
    }
}
