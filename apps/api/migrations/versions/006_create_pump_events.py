"""Story 3.4: Create pump_events table.

Revision ID: 006
Revises: 005
Create Date: 2026-02-06

"""

from collections.abc import Sequence

import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

from alembic import op

# revision identifiers, used by Alembic.
revision: str = "006_pump_events"
down_revision: str | None = "005_glucose"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    conn = op.get_bind()

    # Create pump event type enum (idempotent - check if exists first)
    result = conn.execute(
        sa.text("SELECT 1 FROM pg_type WHERE typname = 'pumpeventtype'")
    )
    if not result.fetchone():
        pump_event_type = postgresql.ENUM(
            "basal",
            "bolus",
            "correction",
            "suspend",
            "resume",
            name="pumpeventtype",
        )
        pump_event_type.create(conn)

    # Create pump_events table
    op.create_table(
        "pump_events",
        sa.Column("id", sa.UUID(), nullable=False),
        sa.Column("user_id", sa.UUID(), nullable=False),
        sa.Column(
            "event_type",
            postgresql.ENUM(
                "basal",
                "bolus",
                "correction",
                "suspend",
                "resume",
                name="pumpeventtype",
                create_type=False,
            ),
            nullable=False,
        ),
        sa.Column("event_timestamp", sa.DateTime(timezone=True), nullable=False),
        sa.Column("units", sa.Float(), nullable=True),
        sa.Column("duration_minutes", sa.Integer(), nullable=True),
        sa.Column("is_automated", sa.Boolean(), nullable=False, server_default="false"),
        sa.Column("control_iq_reason", sa.String(100), nullable=True),
        sa.Column("iob_at_event", sa.Float(), nullable=True),
        sa.Column("cob_at_event", sa.Float(), nullable=True),
        sa.Column("bg_at_event", sa.Integer(), nullable=True),
        sa.Column("received_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("source", sa.String(50), nullable=False, server_default="tandem"),
        sa.ForeignKeyConstraint(
            ["user_id"],
            ["users.id"],
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id"),
    )

    # Create index on user_id for faster lookups
    op.create_index(
        "ix_pump_events_user_id",
        "pump_events",
        ["user_id"],
    )

    # Create composite index for querying recent events
    op.create_index(
        "ix_pump_events_user_timestamp",
        "pump_events",
        ["user_id", "event_timestamp"],
    )

    # Create unique constraint to prevent duplicate events
    op.create_index(
        "ix_pump_events_user_event_unique",
        "pump_events",
        ["user_id", "event_timestamp", "event_type"],
        unique=True,
    )


def downgrade() -> None:
    op.drop_index("ix_pump_events_user_event_unique")
    op.drop_index("ix_pump_events_user_timestamp")
    op.drop_index("ix_pump_events_user_id")
    op.drop_table("pump_events")

    # Drop enum type
    op.execute("DROP TYPE IF EXISTS pumpeventtype")
