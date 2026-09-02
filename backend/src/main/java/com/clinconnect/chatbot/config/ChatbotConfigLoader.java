package com.clinconnect.chatbot.config;

import com.clinconnect.chatbot.config.model.ChatbotIntentToolConfig;
import com.clinconnect.chatbot.config.model.IntentDefinition;
import com.clinconnect.chatbot.config.model.ToolDefinition;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads and structurally validates config/intents.yaml and
 * config/tools.yaml at startup. Spring is the only trusted owner of the
 * intent-to-tool mapping (CLAUDE.md LLM Trust Rules); an unresolvable or
 * malformed mapping is a deployment error and must fail closed, so this
 * loader runs eagerly during bean construction and throws
 * ChatbotConfigException to abort application startup rather than allow the
 * app to run with a broken/partial mapping.
 */
@Component
public class ChatbotConfigLoader {

    private final ChatbotIntentToolConfig config;

    public ChatbotConfigLoader(
            @Value("${clinconnect.config.intents-path}") String intentsPath,
            @Value("${clinconnect.config.tools-path}") String toolsPath) {
        Map<String, Object> intentsDoc = loadYaml(intentsPath);
        Map<String, Object> toolsDoc = loadYaml(toolsPath);

        Map<String, ToolDefinition> toolsById = parseTools(toolsDoc, toolsPath);
        Map<String, IntentDefinition> intentsById = parseIntents(intentsDoc, intentsPath, toolsById);
        Map<String, String> specialtyAliases = parseSpecialtyAliases(intentsDoc);

        this.config = new ChatbotIntentToolConfig(intentsById, toolsById, specialtyAliases);
    }

    public ChatbotIntentToolConfig config() {
        return config;
    }

    private Map<String, Object> loadYaml(String path) {
        Path resolved = Path.of(path);
        if (!Files.isReadable(resolved)) {
            throw new ChatbotConfigException("Config file not found or not readable: " + resolved.toAbsolutePath());
        }
        try (InputStream in = Files.newInputStream(resolved)) {
            Object loaded = new Yaml().load(in);
            if (!(loaded instanceof Map<?, ?> map)) {
                throw new ChatbotConfigException("Config file did not contain a YAML mapping at top level: " + path);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            return typed;
        } catch (IOException e) {
            throw new ChatbotConfigException("Failed to read config file: " + path, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, ToolDefinition> parseTools(Map<String, Object> doc, String path) {
        Object toolsNode = doc.get("tools");
        if (!(toolsNode instanceof Map<?, ?> toolsMap)) {
            throw new ChatbotConfigException("tools.yaml is missing a 'tools' mapping: " + path);
        }
        Map<String, ToolDefinition> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : toolsMap.entrySet()) {
            String toolId = String.valueOf(entry.getKey());
            if (!(entry.getValue() instanceof Map<?, ?> toolBody)) {
                throw new ChatbotConfigException("Tool '" + toolId + "' is not a mapping in tools.yaml");
            }
            Map<String, Object> raw = (Map<String, Object>) toolBody;
            Object scope = raw.get("authorization_scope");
            if (!(scope instanceof String scopeStr) || scopeStr.isBlank()) {
                throw new ChatbotConfigException("Tool '" + toolId + "' is missing authorization_scope in tools.yaml");
            }
            result.put(toolId, new ToolDefinition(toolId, scopeStr, raw));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, IntentDefinition> parseIntents(
            Map<String, Object> doc, String path, Map<String, ToolDefinition> toolsById) {
        Object intentsNode = doc.get("intents");
        if (!(intentsNode instanceof List<?> intentsList)) {
            throw new ChatbotConfigException("intents.yaml is missing an 'intents' list: " + path);
        }
        Map<String, IntentDefinition> result = new LinkedHashMap<>();
        for (Object item : intentsList) {
            if (!(item instanceof Map<?, ?> intentBody)) {
                throw new ChatbotConfigException("An entry in intents.yaml 'intents' is not a mapping");
            }
            Map<String, Object> raw = (Map<String, Object>) intentBody;
            Object idObj = raw.get("id");
            Object toolIdObj = raw.get("tool_id");
            if (!(idObj instanceof String intentId) || intentId.isBlank()) {
                throw new ChatbotConfigException("An intent entry in intents.yaml is missing 'id'");
            }
            if (!(toolIdObj instanceof String toolId) || toolId.isBlank()) {
                throw new ChatbotConfigException("Intent '" + intentId + "' is missing 'tool_id' in intents.yaml");
            }
            if (!toolsById.containsKey(toolId)) {
                throw new ChatbotConfigException(
                        "Intent '" + intentId + "' maps to unknown tool_id '" + toolId
                                + "' — no matching entry in tools.yaml. Failing closed per NFR-006/docs/09-SECURITY.md.");
            }
            if (result.containsKey(intentId)) {
                throw new ChatbotConfigException("Duplicate intent id in intents.yaml: " + intentId);
            }
            result.put(intentId, new IntentDefinition(intentId, toolId, raw));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseSpecialtyAliases(Map<String, Object> intentsDoc) {
        Object aliasesNode = intentsDoc.get("aliases");
        if (!(aliasesNode instanceof Map<?, ?> aliasesMap)) {
            return Map.of();
        }
        Object specialtyNode = aliasesMap.get("specialty");
        if (!(specialtyNode instanceof Map<?, ?> specialtyMap)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : specialtyMap.entrySet()) {
            result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
        return result;
    }
}
