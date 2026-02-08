"""Story 5.6: Create safety_logs table.

Revision ID: 013
Revises: 012
Create Date: 2026-02-08

"""

from collections.abc import Sequence

import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

from alembic import op

# revision identifiers, used by Alembic.
revision: str = "013_safety_logs"
down_revision: str | None = "012_correction_analyses"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "safety_logs",
        sa.Column("id", sa.UUID(), nullable=False),
        sa.Column("user_id", sa.UUID(), nullable=False),
        sa.Column("analysis_type", sa.String(30), nullable=False),
        sa.Column("analysis_id", sa.UUID(), nullable=False),
        sa.Column("status", sa.String(20), nullable=False),
        sa.Column("flagged_items", postgresql.JSON(), nullable=False),
        sa.Column(
            "has_dangerous_content",
            sa.Boolean(),
            nullable=False,
            server_default="false",
        ),
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
        "ix_safety_logs_user_analysis",
        "safety_logs",
        ["user_id", "analysis_type", "analysis_id"],
    )

    op.create_index(
        "ix_safety_logs_user_id",
        "safety_logs",
        ["user_id"],
    )


def downgrade() -> None:
    op.drop_index("ix_safety_logs_user_id")
    op.drop_index("ix_safety_logs_user_analysis")
    op.drop_table("safety_logs")
