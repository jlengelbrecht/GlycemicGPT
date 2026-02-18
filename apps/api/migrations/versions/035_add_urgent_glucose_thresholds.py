"""Add urgent_low and urgent_high columns to target_glucose_ranges.

Extends the target glucose range settings to support all four
configurable thresholds: urgent_low, low_target, high_target, urgent_high.

Revision ID: 035_add_urgent_glucose_thresholds
Revises: 034_add_tandem_pumper_id
"""

import sqlalchemy as sa
from alembic import op

revision = "035_urgent_glucose_thresh"
down_revision = "034_add_tandem_pumper_id"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "target_glucose_ranges",
        sa.Column(
            "urgent_low",
            sa.Float(),
            nullable=False,
            server_default=sa.text("55.0"),
        ),
    )
    op.add_column(
        "target_glucose_ranges",
        sa.Column(
            "urgent_high",
            sa.Float(),
            nullable=False,
            server_default=sa.text("250.0"),
        ),
    )


def downgrade() -> None:
    op.drop_column("target_glucose_ranges", "urgent_high")
    op.drop_column("target_glucose_ranges", "urgent_low")
