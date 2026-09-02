package com.clinconnect.chatbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clinconnect.chatbot.config.model.ChatbotIntentToolConfig;
import org.junit.jupiter.api.Test;

class ChatbotConfigLoaderTest {

    @Test
    void loadsAndValidatesTheRealRepositoryConfig() {
        // Regression test for the harness-validation finding that every
        // intent's tool_id in config/intents.yaml resolves in config/tools.yaml.
        ChatbotIntentToolConfig config =
                new ChatbotConfigLoader("../config/intents.yaml", "../config/tools.yaml").config();

        assertThat(config.intentsById()).hasSize(10);
        assertThat(config.toolsById()).hasSize(9);
        assertThat(config.intentsById().get("triage_consult").toolId()).isEqualTo("get_pcconsult_info");
        config.intentsById().values().forEach(intent ->
                assertThat(config.toolsById()).containsKey(intent.toolId()));

        // FR-015: aliases are configuration-driven, loaded from intents.yaml "aliases.specialty".
        assertThat(config.specialtyAliases())
                .containsEntry("cards", "cardiology")
                .containsEntry("cardio", "cardiology")
                .containsEntry("neuro", "neurology");
    }

    @Test
    void loadsAMinimalValidFixture() {
        ChatbotIntentToolConfig config = new ChatbotConfigLoader(
                "src/test/resources/fixtures/valid-intents.yaml",
                "src/test/resources/fixtures/valid-tools.yaml").config();

        assertThat(config.intentsById()).containsKey("get_locations");
        assertThat(config.toolsById()).containsKey("get_locations");
        assertThat(config.specialtyAliases()).isEmpty();
    }

    @Test
    void failsClosedOnUnknownToolMapping() {
        assertThatThrownBy(() -> new ChatbotConfigLoader(
                "src/test/resources/fixtures/unknown-tool-mapping-intents.yaml",
                "src/test/resources/fixtures/valid-tools.yaml"))
                .isInstanceOf(ChatbotConfigException.class)
                .hasMessageContaining("admin_dump");
    }

    @Test
    void failsClosedOnMissingFile() {
        assertThatThrownBy(() -> new ChatbotConfigLoader(
                "src/test/resources/fixtures/does-not-exist.yaml",
                "src/test/resources/fixtures/valid-tools.yaml"))
                .isInstanceOf(ChatbotConfigException.class);
    }
}
