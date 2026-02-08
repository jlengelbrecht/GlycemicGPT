"""Story 5.3: Create daily_briefs table.

Revision ID: 010
Revises: 009
Create Date: 2026-02-08

"""

from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

# revision identifiers, used by Alembic.
revision: str = "010_daily_briefs"
down_revision: str | None = "009_ai_provider"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "daily_briefs",
        sa.Column("id", sa.UUID(), nullable=False),
        sa.Column("user_id", sa.UUID(), nullable=False),
        sa.Column("period_start", sa.DateTime(timezone=True), nullable=False),
        sa.Column("period_end", sa.DateTime(timezone=True), nullable=False),
        sa.Column("time_in_range_pct", sa.Float(), nullable=False),
        sa.Column("average_glucose", sa.Float(), nullable=False),
        sa.Column("low_count", sa.Integer(), nullable=False),
        sa.Column("high_count", sa.Integer(), nullable=False),
        sa.Column("readings_count", sa.Integer(), nullable=False),
        sa.Column("correction_count", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("total_insulin", sa.Float(), nullable=True),
        sa.Column("ai_summary", sa.Text(), nullable=False),
        sa.Column("ai_model", sa.String(100), nullable=False),
        sa.Column("ai_provider", sa.String(20), nullable=False),
        sa.Column("input_tokens", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("output_tokens", sa.Integer(), nullable=False, server_default="0"),
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
    )

    # Index for querying briefs by user and period
    op.create_index(
        "ix_daily_briefs_user_period",
        "daily_briefs",
        ["user_id", "period_start"],
    )

    # Index for user_id lookups
    op.create_index(
        "ix_daily_briefs_user_id",
        "daily_briefs",
        ["user_id"],
    )


def downgrade() -> None:
    op.drop_index("ix_daily_briefs_user_id")
    op.drop_index("ix_daily_briefs_user_period")
    op.drop_table("daily_briefs")
