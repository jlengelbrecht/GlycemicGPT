"""Story 6.5: Create emergency_contacts table.

Revision ID: 017_emergency_contacts
Revises: 016_alerts
Create Date: 2026-02-09
"""

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "017_emergency_contacts"
down_revision = "016_alerts"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Create enum type using raw SQL to avoid checkfirst issues with asyncpg
    op.execute(sa.text(
        "DO $$ BEGIN "
        "CREATE TYPE contactpriority AS ENUM ('primary', 'secondary'); "
        "EXCEPTION WHEN duplicate_object THEN NULL; "
        "END $$"
    ))

    op.create_table(
        "emergency_contacts",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("name", sa.String(100), nullable=False),
        sa.Column("telegram_username", sa.String(100), nullable=False),
        sa.Column(
            "priority",
            postgresql.ENUM(name="contactpriority", create_type=False),
            nullable=False,
        ),
        sa.Column("position", sa.Integer(), nullable=False, server_default="0"),
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

    # Index for querying contacts by user
    op.create_index(
        "ix_emergency_contacts_user_id",
        "emergency_contacts",
        ["user_id"],
    )

    # Unique constraint: one telegram username per user
    op.create_unique_constraint(
        "uq_emergency_contacts_user_telegram",
        "emergency_contacts",
        ["user_id", "telegram_username"],
    )


def downgrade() -> None:
    op.drop_constraint("uq_emergency_contacts_user_telegram", "emergency_contacts")
    op.drop_index("ix_emergency_contacts_user_id")
    op.drop_table("emergency_contacts")
    op.execute("DROP TYPE IF EXISTS contactpriority")
