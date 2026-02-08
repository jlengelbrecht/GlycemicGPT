"""Story 5.4: Create meal_analyses table.

Revision ID: 011
Revises: 010
Create Date: 2026-02-08

"""

from collections.abc import Sequence

import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

from alembic import op

# revision identifiers, used by Alembic.
revision: str = "011_meal_analyses"
down_revision: str | None = "010_daily_briefs"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "meal_analyses",
        sa.Column("id", sa.UUID(), nullable=False),
        sa.Column("user_id", sa.UUID(), nullable=False),
        sa.Column("period_start", sa.DateTime(timezone=True), nullable=False),
        sa.Column("period_end", sa.DateTime(timezone=True), nullable=False),
        sa.Column("total_boluses", sa.Integer(), nullable=False),
        sa.Column("total_spikes", sa.Integer(), nullable=False),
        sa.Column("avg_post_meal_peak", sa.Float(), nullable=False),
        sa.Column("meal_periods_data", postgresql.JSON(), nullable=False),
        sa.Column("ai_analysis", sa.Text(), nullable=False),
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

    op.create_index(
        "ix_meal_analyses_user_period",
        "meal_analyses",
        ["user_id", "period_start"],
    )

    op.create_index(
        "ix_meal_analyses_user_id",
        "meal_analyses",
        ["user_id"],
    )


def downgrade() -> None:
    op.drop_index("ix_meal_analyses_user_id")
    op.drop_index("ix_meal_analyses_user_period")
    op.drop_table("meal_analyses")
