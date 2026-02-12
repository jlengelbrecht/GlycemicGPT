"""Add sidecar_provider column and make encrypted_api_key nullable.

Subscription providers (claude_subscription, chatgpt_subscription) use
the managed AI sidecar for authentication instead of user-provided API
keys. This migration:

1. Adds sidecar_provider (String, nullable) to track which sidecar
   provider is active ("claude" or "codex").
2. Makes encrypted_api_key nullable so subscription types can omit it.

Revision ID: 030_ai_provider_sidecar_fields
Revises: 029_insulin_config_bg_reading
"""

import sqlalchemy as sa
from alembic import op

revision = "030_ai_provider_sidecar_fields"
down_revision = "029_insulin_config_bg_reading"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Add sidecar_provider column (nullable — only set for subscription types)
    op.add_column(
        "ai_provider_configs",
        sa.Column("sidecar_provider", sa.String(50), nullable=True),
    )

    # Make encrypted_api_key nullable for subscription types that use
    # sidecar OAuth instead of a user-provided API key
    op.alter_column(
        "ai_provider_configs",
        "encrypted_api_key",
        existing_type=sa.Text(),
        nullable=True,
    )


def downgrade() -> None:
    # Restore NOT NULL on encrypted_api_key (backfill NULLs first)
    op.execute(
        "UPDATE ai_provider_configs "
        "SET encrypted_api_key = 'migrated-placeholder' "
        "WHERE encrypted_api_key IS NULL"
    )
    op.alter_column(
        "ai_provider_configs",
        "encrypted_api_key",
        existing_type=sa.Text(),
        nullable=False,
    )

    op.drop_column("ai_provider_configs", "sidecar_provider")
