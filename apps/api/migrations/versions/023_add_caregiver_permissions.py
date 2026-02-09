"""Story 8.2: Add permission columns to caregiver_links table.

Revision ID: 023_caregiver_permissions
Revises: 022_caregiver_invitations
Create Date: 2026-02-09
"""

import sqlalchemy as sa
from alembic import op

revision = "023_caregiver_permissions"
down_revision = "022_caregiver_invitations"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "caregiver_links",
        sa.Column(
            "can_view_glucose",
            sa.Boolean(),
            nullable=False,
            server_default=sa.text("true"),
        ),
    )
    op.add_column(
        "caregiver_links",
        sa.Column(
            "can_view_history",
            sa.Boolean(),
            nullable=False,
            server_default=sa.text("true"),
        ),
    )
    op.add_column(
        "caregiver_links",
        sa.Column(
            "can_view_iob",
            sa.Boolean(),
            nullable=False,
            server_default=sa.text("true"),
        ),
    )
    op.add_column(
        "caregiver_links",
        sa.Column(
            "can_view_ai_suggestions",
            sa.Boolean(),
            nullable=False,
            server_default=sa.text("false"),
        ),
    )
    op.add_column(
        "caregiver_links",
        sa.Column(
            "can_receive_alerts",
            sa.Boolean(),
            nullable=False,
            server_default=sa.text("true"),
        ),
    )


def downgrade() -> None:
    op.drop_column("caregiver_links", "can_receive_alerts")
    op.drop_column("caregiver_links", "can_view_ai_suggestions")
    op.drop_column("caregiver_links", "can_view_iob")
    op.drop_column("caregiver_links", "can_view_history")
    op.drop_column("caregiver_links", "can_view_glucose")
