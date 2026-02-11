"""Story 14.1: Expand AI provider types and add base_url.

Adds 5 new enum values to aiprovidertype (total 7 with legacy claude/openai),
migrates existing rows from legacy to new values, and adds base_url column.

Revision ID: 028_expand_ai_providers
Revises: 027_add_display_name
"""

import sqlalchemy as sa
from alembic import op

revision = "028_expand_ai_providers"
down_revision = "027_add_display_name"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Add new enum values - must run outside transaction for PostgreSQL
    # ALTER TYPE ... ADD VALUE IF NOT EXISTS is non-transactional
    op.execute("COMMIT")
    op.execute(
        "ALTER TYPE aiprovidertype ADD VALUE IF NOT EXISTS 'claude_api'"
    )
    op.execute(
        "ALTER TYPE aiprovidertype ADD VALUE IF NOT EXISTS 'openai_api'"
    )
    op.execute(
        "ALTER TYPE aiprovidertype ADD VALUE IF NOT EXISTS 'claude_subscription'"
    )
    op.execute(
        "ALTER TYPE aiprovidertype ADD VALUE IF NOT EXISTS 'chatgpt_subscription'"
    )
    op.execute(
        "ALTER TYPE aiprovidertype ADD VALUE IF NOT EXISTS 'openai_compatible'"
    )

    # Re-establish transaction for data migration and schema changes
    op.execute("BEGIN")

    # Migrate existing rows to new enum values
    op.execute(
        "UPDATE ai_provider_configs SET provider_type = 'claude_api' "
        "WHERE provider_type = 'claude'"
    )
    op.execute(
        "UPDATE ai_provider_configs SET provider_type = 'openai_api' "
        "WHERE provider_type = 'openai'"
    )

    # Add base_url column
    op.add_column(
        "ai_provider_configs",
        sa.Column("base_url", sa.String(500), nullable=True),
    )


def downgrade() -> None:
    # Remove base_url column
    op.drop_column("ai_provider_configs", "base_url")

    # Delete rows with provider types that don't exist in the old schema.
    # These cannot be mapped back to claude/openai.
    op.execute(
        "DELETE FROM ai_provider_configs WHERE provider_type IN "
        "('claude_subscription', 'chatgpt_subscription', 'openai_compatible')"
    )

    # Migrate remaining rows back to old enum values
    op.execute(
        "UPDATE ai_provider_configs SET provider_type = 'claude' "
        "WHERE provider_type = 'claude_api'"
    )
    op.execute(
        "UPDATE ai_provider_configs SET provider_type = 'openai' "
        "WHERE provider_type = 'openai_api'"
    )
    # Note: PostgreSQL cannot remove enum values, so the new type values
    # remain in the enum but no rows will reference them after this downgrade.
