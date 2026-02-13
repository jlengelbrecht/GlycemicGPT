"""Application configuration using Pydantic Settings."""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Application settings loaded from environment variables."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
    )

    # Database
    database_url: str = (
        "postgresql+asyncpg://glycemicgpt:glycemicgpt@localhost:5432/glycemicgpt"
    )

    # Redis
    redis_url: str = "redis://localhost:6379/0"

    # Security
    secret_key: str = "change-me-in-production"
    jwt_algorithm: str = "HS256"
    jwt_cookie_name: str = "glycemicgpt_session"

    # Logging
    log_format: str = "json"  # 'json' or 'text'
    log_level: str = "INFO"
    service_name: str = "glycemicgpt-api"

    # CORS
    cors_origins: list[str] = ["http://localhost:3000"]

    # Session
    session_expire_hours: int = 24
    cookie_secure: bool = (
        True  # Set to False for plain HTTP (e.g. Docker integration tests)
    )

    # Backup Configuration (Story 1.5)
    backup_enabled: bool = True
    backup_schedule: str = "0 2 * * *"  # Cron: daily at 2 AM
    backup_path: str = "/backups"
    backup_retention_days: int = 7
    # Sync database URL for pg_dump (no asyncpg)
    database_sync_url: str = (
        "postgresql://glycemicgpt:glycemicgpt@localhost:5432/glycemicgpt"
    )

    # Data Sync Configuration (Story 3.2)
    dexcom_sync_interval_minutes: int = 5  # Sync every 5 minutes
    dexcom_sync_enabled: bool = True  # Enable/disable automatic sync
    dexcom_max_readings_per_sync: int = 12  # Max readings to fetch per sync (1 hour)

    # Tandem Sync Configuration (Story 3.4)
    tandem_sync_interval_minutes: int = 60  # Sync every hour
    tandem_sync_enabled: bool = True  # Enable/disable automatic sync
    tandem_sync_hours_back: int = 24  # Hours of history to fetch per sync

    # Predictive Alert Engine (Story 6.2)
    alert_check_interval_minutes: int = 5  # Run alert engine every 5 minutes
    alert_check_enabled: bool = True  # Enable/disable automatic alert checking

    # Alert Escalation (Story 6.7)
    escalation_check_interval_minutes: int = 1  # Check every 1 minute
    escalation_check_enabled: bool = True  # Enable/disable automatic escalation

    # Data Retention (Story 9.3)
    data_retention_enabled: bool = True
    data_retention_check_interval_hours: int = 24  # Run daily

    # Telegram Bot (Story 7.1)
    telegram_bot_token: str = ""
    telegram_polling_enabled: bool = True
    telegram_polling_interval_seconds: int = 5

    # Tandem Cloud Upload (Story 16.6)
    tandem_upload_enabled: bool = True
    tandem_upload_check_interval_minutes: int = 1  # Check for due uploads every minute
    tandem_upload_hmac_key: str = ""  # HMAC-SHA1 key for TDCToken signing
    tandem_upload_client_id: str = ""  # Okta client ID for Tandem SSO
    tandem_upload_sso_base: str = "https://sso.tandemdiabetes.com"
    tandem_upload_config_base: str = "https://assets.tandemdiabetes.com"

    # AI Sidecar (Story 15.2)
    ai_sidecar_url: str = "http://ai-sidecar:3456"
    ai_sidecar_api_key: str = ""  # SIDECAR_API_KEY for inter-service auth

    # Testing
    testing: bool = False  # Set to True during tests to disable connection pooling


settings = Settings()
