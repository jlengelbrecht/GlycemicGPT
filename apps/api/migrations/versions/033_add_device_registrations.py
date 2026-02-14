"""Create device_registrations table for mobile alert delivery.

Revision ID: 033_add_device_registrations
Revises: 032_add_tandem_cloud_upload
"""

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "033_add_device_registrations"
down_revision = "032_add_tandem_cloud_upload"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "device_registrations",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("device_token", sa.String(255), nullable=False),
        sa.Column("device_name", sa.String(255), nullable=False),
        sa.Column("platform", sa.String(50), nullable=False, server_default="android"),
        sa.Column(
            "last_seen_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("device_token", name="uq_device_registration_token"),
    )
    op.create_index(
        "ix_device_registrations_user_id",
        "device_registrations",
        ["user_id"],
    )


def downgrade() -> None:
    op.drop_index("ix_device_registrations_user_id", table_name="device_registrations")
    op.drop_table("device_registrations")
