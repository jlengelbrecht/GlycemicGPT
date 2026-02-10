"""Story 9.1: Create target_glucose_ranges table.

Revision ID: 024_target_glucose_ranges
Revises: 023_add_caregiver_permissions
Create Date: 2026-02-09
"""

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "024_target_glucose_ranges"
down_revision = "023_caregiver_permissions"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "target_glucose_ranges",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
            unique=True,
        ),
        sa.Column("low_target", sa.Float(), nullable=False, server_default="70.0"),
        sa.Column("high_target", sa.Float(), nullable=False, server_default="180.0"),
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
            nullable=False,
        ),
    )
    op.create_index(
        "ix_target_glucose_ranges_user_id",
        "target_glucose_ranges",
        ["user_id"],
    )


def downgrade() -> None:
    op.drop_index("ix_target_glucose_ranges_user_id")
    op.drop_table("target_glucose_ranges")
