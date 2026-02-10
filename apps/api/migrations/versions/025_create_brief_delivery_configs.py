"""Story 9.2: Create brief_delivery_configs table.

Revision ID: 025_brief_delivery_configs
Revises: 024_target_glucose_ranges
"""

import sqlalchemy as sa
from alembic import op

revision = "025_brief_delivery_configs"
down_revision = "024_target_glucose_ranges"
branch_labels = None
depends_on = None


def upgrade() -> None:
    delivery_channel_enum = sa.Enum(
        "web_only", "telegram", "both", name="deliverychannel"
    )

    op.create_table(
        "brief_delivery_configs",
        sa.Column(
            "id",
            sa.dialects.postgresql.UUID(as_uuid=True),
            primary_key=True,
        ),
        sa.Column(
            "user_id",
            sa.dialects.postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
            unique=True,
        ),
        sa.Column("enabled", sa.Boolean, nullable=False, server_default="true"),
        sa.Column(
            "delivery_time", sa.Time, nullable=False, server_default="07:00:00"
        ),
        sa.Column(
            "timezone",
            sa.String(64),
            nullable=False,
            server_default="UTC",
        ),
        sa.Column(
            "channel",
            delivery_channel_enum,
            nullable=False,
            server_default="both",
        ),
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

    op.create_index(
        "ix_brief_delivery_configs_user_id",
        "brief_delivery_configs",
        ["user_id"],
    )


def downgrade() -> None:
    op.drop_index("ix_brief_delivery_configs_user_id")
    op.drop_table("brief_delivery_configs")
    op.execute("DROP TYPE IF EXISTS deliverychannel")
