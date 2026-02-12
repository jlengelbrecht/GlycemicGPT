"""Add insulin_configs table and bg_reading pump event type.

Creates the insulin_configs table for storing user insulin type and DIA
configuration. Also adds 'bg_reading' to the pumpeventtype enum for
ingesting IoB data from Tandem LidBgReadingTaken events.

Revision ID: 029_insulin_config_bg_reading
Revises: 028_expand_ai_providers
"""

import sqlalchemy as sa
from alembic import op

revision = "029_insulin_config_bg_reading"
down_revision = "028_expand_ai_providers"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Add bg_reading to pumpeventtype enum (non-transactional for PostgreSQL)
    op.execute("COMMIT")
    op.execute(
        "ALTER TYPE pumpeventtype ADD VALUE IF NOT EXISTS 'bg_reading'"
    )

    # Start a new transaction for the table creation
    op.execute("BEGIN")

    # Create insulin_configs table
    op.create_table(
        "insulin_configs",
        sa.Column("id", sa.dialects.postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "user_id",
            sa.dialects.postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
            unique=True,
        ),
        sa.Column("insulin_type", sa.String(50), nullable=False, server_default="humalog"),
        sa.Column("dia_hours", sa.Float, nullable=False, server_default="4.0"),
        sa.Column("onset_minutes", sa.Float, nullable=False, server_default="15.0"),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            onupdate=sa.func.now(),
            nullable=False,
        ),
    )

    op.create_index("ix_insulin_configs_user_id", "insulin_configs", ["user_id"])


def downgrade() -> None:
    op.drop_index("ix_insulin_configs_user_id")
    op.drop_table("insulin_configs")
    # Note: Cannot remove enum values in PostgreSQL without recreating the type
