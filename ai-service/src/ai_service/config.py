from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Shared app-level environment configuration (.env.example)."""

    model_config = SettingsConfigDict(env_file=None, extra="ignore")

    app_env: str = "local"
    log_level: str = "INFO"

    ollama_base_url: str = "http://localhost:11434"
    ollama_model: str = "select-before-phase-2"
    ollama_timeout_seconds: float = 60
    ai_max_retries_for_invalid_structured_output: int = 1

    intents_config_path: str = "../config/intents.yaml"
    intent_router_prompt_path: str = "../config/prompts/intent-router.md"
    clarification_prompt_path: str = "../config/prompts/clarification.md"


settings = Settings()
