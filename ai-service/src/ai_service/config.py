from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Shared app-level environment configuration (.env.example)."""

    model_config = SettingsConfigDict(env_file=None, extra="ignore")

    app_env: str = "local"
    log_level: str = "INFO"

    ollama_base_url: str = "http://localhost:11434"
    ollama_model: str = "select-before-phase-2"
    ollama_timeout_seconds: float = 60
    # Phase 7 POC hardening: single bounded retry, after this delay, on a transport-level
    # Ollama failure (connection refused/reset, timeout) only — not a well-formed HTTP error
    # response. See ollama_client.generate_json.
    ollama_retry_delay_seconds: float = 0.3
    ai_max_retries_for_invalid_structured_output: int = 1

    intents_config_path: str = "../config/intents.yaml"
    intent_router_prompt_path: str = "../config/prompts/intent-router.md"
    clarification_prompt_path: str = "../config/prompts/clarification.md"


settings = Settings()
