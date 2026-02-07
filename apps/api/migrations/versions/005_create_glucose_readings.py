"""Story 3.2: Create glucose_readings table.

Revision ID: 005
Revises: 004
Create Date: 2026-02-06

"""

from collections.abc import Sequence

import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

from alembic import op

# revision identifiers, used by Alembic.
revision: str = "005_glucose"
down_revision: str | None = "004_integrations"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    # Create trend direction enum
    trend_direction = postgresql.ENUM(
        "double_up",
        "single_up",
        "forty_five_up",
        "flat",
        "forty_five_down",
        "single_down",
        "double_down",
        "not_computable",
        "rate_out_of_range",
        name="trenddirection",
    )
    trend_direction.create(op.get_bind())

    # Create glucose_readings table
    op.create_table(
        "glucose_readings",
        sa.Column("id", sa.UUID(), nullable=False),
        sa.Column("user_id", sa.UUID(), nullable=False),
        sa.Column("value", sa.Integer(), nullable=False),
        sa.Column("reading_timestamp", sa.DateTime(timezone=True), nullable=False),
        sa.Column(
            "trend",
            postgresql.ENUM(
                "double_up",
                "single_up",
                "forty_five_up",
                "flat",
                "forty_five_down",
                "single_down",
                "double_down",
                "not_computable",
                "rate_out_of_range",
                name="trenddirection",
                create_type=False,
            ),
            nullable=False,
        ),
        sa.Column("trend_rate", sa.Float(), nullable=True),
        sa.Column("received_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("source", sa.String(), nullable=False, server_default="dexcom"),
        sa.ForeignKeyConstraint(
            ["user_id"],
            ["users.id"],
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id"),
    )

    # Create index on user_id for faster lookups
    op.create_index(
        "ix_glucose_readings_user_id",
        "glucose_readings",
        ["user_id"],
    )

    # Create composite index for querying recent readings
    op.create_index(
        "ix_glucose_readings_user_timestamp",
        "glucose_readings",
        ["user_id", "reading_timestamp"],
    )

    # Create unique constraint to prevent duplicate readings
    op.create_index(
        "ix_glucose_readings_user_reading",
        "glucose_readings",
        ["user_id", "reading_timestamp"],
        unique=True,
    )


def downgrade() -> None:
    op.drop_index("ix_glucose_readings_user_reading")
    op.drop_index("ix_glucose_readings_user_timestamp")
    op.drop_index("ix_glucose_readings_user_id")
    op.drop_table("glucose_readings")

    # Drop enum type
    op.execute("DROP TYPE IF EXISTS trenddirection")
