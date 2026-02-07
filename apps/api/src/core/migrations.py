"""Database migration utilities."""

import logging
from pathlib import Path

from alembic import command
from alembic.config import Config
from sqlalchemy import text

from src.database import get_engine

logger = logging.getLogger(__name__)


def get_alembic_config() -> Config:
    """Get Alembic configuration."""
    # Find the alembic.ini file relative to the app root
    app_root = Path(__file__).parent.parent.parent
    alembic_ini = app_root / "alembic.ini"

    if not alembic_ini.exists():
        raise FileNotFoundError(f"alembic.ini not found at {alembic_ini}")

    config = Config(str(alembic_ini))
    config.set_main_option("script_location", str(app_root / "migrations"))

    return config


def run_migrations() -> None:
    """
    Run all pending database migrations synchronously.

    This uses Alembic's built-in command which handles async internally.
    Should be called during application startup to ensure
    the database schema is up to date.
    """
    logger.info("Running database migrations...")

    try:
        config = get_alembic_config()
        command.upgrade(config, "head")
        logger.info("Database migrations completed successfully")
    except Exception as e:
        logger.error(f"Database migration failed: {e}")
        raise


async def check_migrations_current() -> bool:
    """
    Check if all migrations have been applied.

    Returns:
        True if database is at latest migration, False otherwise.
    """
    try:
        async with get_engine().connect() as conn:
            # Check if alembic_version table exists and has a version
            result = await conn.execute(
                text("SELECT version_num FROM alembic_version LIMIT 1")
            )
            row = result.fetchone()
            return row is not None
    except Exception:
        return False


def get_current_revision() -> str | None:
    """Get the current database revision."""
    try:
        config = get_alembic_config()
        from alembic.script import ScriptDirectory

        script = ScriptDirectory.from_config(config)
        head = script.get_current_head()
        return head
    except Exception:
        return None
