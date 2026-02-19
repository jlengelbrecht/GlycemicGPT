"""Add battery and reservoir pump event types.

Adds 'battery' and 'reservoir' to the pumpeventtype enum so the mobile
app can sync battery percentage and reservoir level to the backend.

Revision ID: 036_battery_reservoir
Revises: 035_urgent_glucose_thresh
"""

from alembic import op

revision = "036_battery_reservoir"
down_revision = "035_urgent_glucose_thresh"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # ALTER TYPE ... ADD VALUE must run outside a transaction in PostgreSQL
    op.execute("COMMIT")
    op.execute(
        "ALTER TYPE pumpeventtype ADD VALUE IF NOT EXISTS 'battery'"
    )
    op.execute(
        "ALTER TYPE pumpeventtype ADD VALUE IF NOT EXISTS 'reservoir'"
    )


def downgrade() -> None:
    # Cannot remove enum values in PostgreSQL without recreating the type
    pass
