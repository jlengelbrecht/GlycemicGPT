"""Story 9.3: Create data_retention_configs table.

Revision ID: 026_data_retention_configs
Revises: 025_brief_delivery_configs
"""

import sqlalchemy as sa
from alembic import op

revision = "026_data_retention_configs"
down_revision = "025_brief_delivery_configs"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "data_retention_configs",
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
        sa.Column(
            "glucose_retention_days",
            sa.Integer,
            nullable=False,
            server_default="365",
        ),
        sa.Column(
            "analysis_retention_days",
            sa.Integer,
            nullable=False,
            server_default="365",
        ),
        sa.Column(
            "audit_retention_days",
            sa.Integer,
            nullable=False,
            server_default="730",
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
        "ix_data_retention_configs_user_id",
        "data_retention_configs",
        ["user_id"],
    )


def downgrade() -> None:
    op.drop_index("ix_data_retention_configs_user_id")
    op.drop_table("data_retention_configs")
