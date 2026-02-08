"""Story 5.1: Create ai_provider_configs table.

Revision ID: 009
Revises: 008
Create Date: 2026-02-08

"""

from collections.abc import Sequence

import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

from alembic import op

# revision identifiers, used by Alembic.
revision: str = "009_ai_provider"
down_revision: str | None = "008_control_iq"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    # Create enum type for AI provider
    ai_provider_type = postgresql.ENUM(
        "claude", "openai",
        name="aiprovidertype",
    )
    ai_provider_type.create(op.get_bind())

    # Create enum type for AI provider status
    ai_provider_status = postgresql.ENUM(
        "connected", "error", "pending",
        name="aiproviderstatus",
    )
    ai_provider_status.create(op.get_bind())

    # Create ai_provider_configs table
    op.create_table(
        "ai_provider_configs",
        sa.Column("id", sa.UUID(), nullable=False),
        sa.Column("user_id", sa.UUID(), nullable=False),
        sa.Column(
            "provider_type",
            postgresql.ENUM(
                "claude", "openai",
                name="aiprovidertype",
                create_type=False,
            ),
            nullable=False,
        ),
        sa.Column("encrypted_api_key", sa.Text(), nullable=False),
        sa.Column("model_name", sa.String(100), nullable=True),
        sa.Column(
            "status",
            postgresql.ENUM(
                "connected", "error", "pending",
                name="aiproviderstatus",
                create_type=False,
            ),
            nullable=False,
            server_default="connected",
        ),
        sa.Column("last_validated_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("last_error", sa.Text(), nullable=True),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(
            ["user_id"],
            ["users.id"],
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("user_id", name="uq_ai_provider_user"),
    )

    # Create index on user_id for faster lookups
    op.create_index(
        "ix_ai_provider_configs_user_id",
        "ai_provider_configs",
        ["user_id"],
    )


def downgrade() -> None:
    op.drop_index("ix_ai_provider_configs_user_id")
    op.drop_table("ai_provider_configs")

    # Drop enum types
    op.execute("DROP TYPE IF EXISTS aiproviderstatus")
    op.execute("DROP TYPE IF EXISTS aiprovidertype")
