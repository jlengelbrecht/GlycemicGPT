"""Create pump_profiles table for storing Tandem pump settings profiles.

Stores time-segmented pump profiles (basal rates, correction factors,
carb ratios, target BG) synced from Tandem t:connect API.

Revision ID: 031_create_pump_profiles
Revises: 030_ai_provider_sidecar_fields
"""

import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

from alembic import op

revision = "031_create_pump_profiles"
down_revision = "030_ai_provider_sidecar_fields"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "pump_profiles",
        sa.Column(
            "id",
            postgresql.UUID(as_uuid=True),
            nullable=False,
        ),
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("profile_name", sa.String(100), nullable=False),
        sa.Column("is_active", sa.Boolean(), nullable=False, server_default="false"),
        sa.Column("segments", postgresql.JSONB(), nullable=False, server_default="[]"),
        sa.Column("insulin_duration_min", sa.Integer(), nullable=True),
        sa.Column("carb_entry_enabled", sa.Boolean(), nullable=False, server_default="true"),
        sa.Column("max_bolus_units", sa.Float(), nullable=True),
        sa.Column("cgm_high_alert_mgdl", sa.Integer(), nullable=True),
        sa.Column("cgm_low_alert_mgdl", sa.Integer(), nullable=True),
        sa.Column(
            "synced_at",
            sa.DateTime(timezone=True),
            nullable=False,
        ),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("user_id", "profile_name", name="uq_pump_profile_user_name"),
    )
    op.create_index(
        "ix_pump_profiles_user_id",
        "pump_profiles",
        ["user_id"],
    )


def downgrade() -> None:
    op.drop_index("ix_pump_profiles_user_id", table_name="pump_profiles")
    op.drop_table("pump_profiles")
