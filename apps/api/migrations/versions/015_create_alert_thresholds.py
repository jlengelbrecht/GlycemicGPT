"""Story 6.1: Create alert_thresholds table.

Revision ID: 015_alert_thresholds
Revises: 014_suggestion_responses
Create Date: 2026-02-09
"""

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "015_alert_thresholds"
down_revision = "014_suggestion_responses"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "alert_thresholds",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
            unique=True,
        ),
        sa.Column("low_warning", sa.Float(), nullable=False, server_default="70.0"),
        sa.Column("urgent_low", sa.Float(), nullable=False, server_default="55.0"),
        sa.Column("high_warning", sa.Float(), nullable=False, server_default="180.0"),
        sa.Column("urgent_high", sa.Float(), nullable=False, server_default="250.0"),
        sa.Column("iob_warning", sa.Float(), nullable=False, server_default="3.0"),
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
        "ix_alert_thresholds_user_id",
        "alert_thresholds",
        ["user_id"],
    )


def downgrade() -> None:
    op.drop_index("ix_alert_thresholds_user_id")
    op.drop_table("alert_thresholds")
