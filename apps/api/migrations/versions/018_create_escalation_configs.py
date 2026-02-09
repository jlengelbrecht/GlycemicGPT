"""Story 6.6: Create escalation_configs table.

Revision ID: 018_escalation_configs
Revises: 017_emergency_contacts
Create Date: 2026-02-09
"""

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "018_escalation_configs"
down_revision = "017_emergency_contacts"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "escalation_configs",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
            unique=True,
        ),
        sa.Column(
            "reminder_delay_minutes",
            sa.Integer(),
            nullable=False,
            server_default="5",
        ),
        sa.Column(
            "primary_contact_delay_minutes",
            sa.Integer(),
            nullable=False,
            server_default="10",
        ),
        sa.Column(
            "all_contacts_delay_minutes",
            sa.Integer(),
            nullable=False,
            server_default="20",
        ),
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
        "ix_escalation_configs_user_id",
        "escalation_configs",
        ["user_id"],
    )


def downgrade() -> None:
    op.drop_index("ix_escalation_configs_user_id")
    op.drop_table("escalation_configs")
