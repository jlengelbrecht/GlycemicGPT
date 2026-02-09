"""Story 6.7: Create escalation_events table.

Revision ID: 019_escalation_events
Revises: 018_escalation_configs
Create Date: 2026-02-09
"""

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "019_escalation_events"
down_revision = "018_escalation_configs"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Create enums
    escalation_tier = postgresql.ENUM(
        "reminder",
        "primary_contact",
        "all_contacts",
        name="escalationtier",
        create_type=False,
    )
    notification_status = postgresql.ENUM(
        "pending",
        "sent",
        "failed",
        name="notificationstatus",
        create_type=False,
    )
    escalation_tier.create(op.get_bind(), checkfirst=True)
    notification_status.create(op.get_bind(), checkfirst=True)

    op.create_table(
        "escalation_events",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "alert_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("alerts.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "tier",
            escalation_tier,
            nullable=False,
        ),
        sa.Column(
            "triggered_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
        sa.Column(
            "message_content",
            sa.Text(),
            nullable=False,
        ),
        sa.Column(
            "notification_status",
            notification_status,
            nullable=False,
            server_default="pending",
        ),
        sa.Column(
            "contacts_notified",
            postgresql.JSONB(),
            nullable=False,
            server_default="[]",
        ),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
    )

    op.create_index(
        "ix_escalation_events_alert_id",
        "escalation_events",
        ["alert_id"],
    )
    op.create_index(
        "ix_escalation_events_user_id",
        "escalation_events",
        ["user_id"],
    )
    # Unique constraint: one event per tier per alert (idempotency)
    op.create_unique_constraint(
        "uq_escalation_events_alert_tier",
        "escalation_events",
        ["alert_id", "tier"],
    )


def downgrade() -> None:
    op.drop_constraint("uq_escalation_events_alert_tier", "escalation_events")
    op.drop_index("ix_escalation_events_user_id")
    op.drop_index("ix_escalation_events_alert_id")
    op.drop_table("escalation_events")
    op.execute("DROP TYPE IF EXISTS notificationstatus")
    op.execute("DROP TYPE IF EXISTS escalationtier")
