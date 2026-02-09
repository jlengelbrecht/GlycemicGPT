"""Story 5.7: Create suggestion_responses table.

Revision ID: 014
Revises: 013
Create Date: 2026-02-08

"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

# revision identifiers, used by Alembic.
revision: str = "014_suggestion_responses"
down_revision: str | None = "013_safety_logs"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "suggestion_responses",
        sa.Column("id", sa.UUID(), nullable=False),
        sa.Column("user_id", sa.UUID(), nullable=False),
        sa.Column("analysis_type", sa.String(30), nullable=False),
        sa.Column("analysis_id", sa.UUID(), nullable=False),
        sa.Column("response", sa.String(20), nullable=False),
        sa.Column("reason", sa.String(500), nullable=True),
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
        "ix_suggestion_responses_user_analysis",
        "suggestion_responses",
        ["user_id", "analysis_type", "analysis_id"],
    )

    op.create_index(
        "ix_suggestion_responses_user_id",
        "suggestion_responses",
        ["user_id"],
    )


def downgrade() -> None:
    op.drop_index("ix_suggestion_responses_user_id")
    op.drop_index("ix_suggestion_responses_user_analysis")
    op.drop_table("suggestion_responses")
