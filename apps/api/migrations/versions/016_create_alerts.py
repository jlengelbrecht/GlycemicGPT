"""Story 6.2: Create alerts table for predictive alert engine.

Revision ID: 016_alerts
Revises: 015_alert_thresholds
Create Date: 2026-02-09
"""

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "016_alerts"
down_revision = "015_alert_thresholds"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Create enum types using raw SQL to avoid checkfirst issues with asyncpg
    op.execute(sa.text(
        "DO $$ BEGIN "
        "CREATE TYPE alerttype AS ENUM "
        "('low_urgent', 'low_warning', 'high_warning', 'high_urgent', 'iob_warning'); "
        "EXCEPTION WHEN duplicate_object THEN NULL; "
        "END $$"
    ))
    op.execute(sa.text(
        "DO $$ BEGIN "
        "CREATE TYPE alertseverity AS ENUM "
        "('info', 'warning', 'urgent', 'emergency'); "
        "EXCEPTION WHEN duplicate_object THEN NULL; "
        "END $$"
    ))

    op.create_table(
        "alerts",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "alert_type",
            postgresql.ENUM(name="alerttype", create_type=False),
            nullable=False,
        ),
        sa.Column(
            "severity",
            postgresql.ENUM(name="alertseverity", create_type=False),
            nullable=False,
        ),
        sa.Column("current_value", sa.Float(), nullable=False),
        sa.Column("predicted_value", sa.Float(), nullable=True),
        sa.Column("prediction_minutes", sa.Integer(), nullable=True),
        sa.Column("iob_value", sa.Float(), nullable=True),
        sa.Column("message", sa.Text(), nullable=False),
        sa.Column("trend_rate", sa.Float(), nullable=True),
        sa.Column(
            "source",
            sa.String(50),
            nullable=False,
            server_default="predictive",
        ),
        sa.Column(
            "acknowledged",
            sa.Boolean(),
            nullable=False,
            server_default="false",
        ),
        sa.Column("acknowledged_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
        sa.Column(
            "expires_at",
            sa.DateTime(timezone=True),
            nullable=False,
        ),
    )

    # Index for querying active alerts for a user
    op.create_index(
        "ix_alerts_user_id",
        "alerts",
        ["user_id"],
    )

    # Index for querying unacknowledged alerts
    op.create_index(
        "ix_alerts_user_acknowledged",
        "alerts",
        ["user_id", "acknowledged"],
    )

    # Index for deduplication: prevent duplicate alerts of same type within window
    op.create_index(
        "ix_alerts_user_type_created",
        "alerts",
        ["user_id", "alert_type", "created_at"],
    )


def downgrade() -> None:
    op.drop_index("ix_alerts_user_type_created")
    op.drop_index("ix_alerts_user_acknowledged")
    op.drop_index("ix_alerts_user_id")
    op.drop_table("alerts")

    # Drop enum types
    op.execute("DROP TYPE IF EXISTS alertseverity")
    op.execute("DROP TYPE IF EXISTS alerttype")
