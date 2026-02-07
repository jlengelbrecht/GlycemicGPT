"""Story 3.5: Add Control-IQ activity fields to pump_events.

Revision ID: 008_control_iq
Revises: 007_region
Create Date: 2026-02-06

"""

from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

# revision identifiers, used by Alembic.
revision: str = "008_control_iq"
down_revision: str | None = "007_region"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    # Add control_iq_mode column for tracking Sleep/Exercise/Standard mode
    op.add_column(
        "pump_events",
        sa.Column("control_iq_mode", sa.String(20), nullable=True),
    )

    # Add basal_adjustment_pct for tracking basal rate changes
    # Positive = increase, Negative = decrease
    op.add_column(
        "pump_events",
        sa.Column("basal_adjustment_pct", sa.Float(), nullable=True),
    )


def downgrade() -> None:
    op.drop_column("pump_events", "basal_adjustment_pct")
    op.drop_column("pump_events", "control_iq_mode")
