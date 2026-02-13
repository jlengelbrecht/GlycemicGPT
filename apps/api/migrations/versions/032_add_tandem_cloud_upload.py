"""Create tables for Tandem cloud upload pipeline.

Adds pump_raw_events, pump_hardware_info, and tandem_upload_state tables
to support uploading pump data to the Tandem cloud on behalf of the user.

Revision ID: 032_add_tandem_cloud_upload
Revises: 031_create_pump_profiles
"""

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "032_add_tandem_cloud_upload"
down_revision = "031_create_pump_profiles"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # -- pump_raw_events --
    op.create_table(
        "pump_raw_events",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("sequence_number", sa.Integer(), nullable=False),
        sa.Column("raw_bytes_b64", sa.Text(), nullable=False),
        sa.Column("event_type_id", sa.Integer(), nullable=False),
        sa.Column("pump_time_seconds", sa.BigInteger(), nullable=False),
        sa.Column(
            "uploaded_to_tandem",
            sa.Boolean(),
            nullable=False,
            server_default="false",
        ),
        sa.Column("uploaded_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint(
            "user_id", "sequence_number", name="uq_pump_raw_event_user_seq"
        ),
        comment="Raw BLE bytes from Tandem pump for cloud upload",
    )
    op.create_index(
        "ix_pump_raw_events_user_id",
        "pump_raw_events",
        ["user_id"],
    )
    op.create_index(
        "ix_pump_raw_events_user_uploaded",
        "pump_raw_events",
        ["user_id", "uploaded_to_tandem"],
    )

    # -- pump_hardware_info --
    op.create_table(
        "pump_hardware_info",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
            unique=True,
        ),
        sa.Column("serial_number", sa.BigInteger(), nullable=False),
        sa.Column("model_number", sa.BigInteger(), nullable=False),
        sa.Column("part_number", sa.BigInteger(), nullable=False),
        sa.Column("pump_rev", sa.String(50), nullable=False),
        sa.Column("arm_sw_ver", sa.BigInteger(), nullable=False),
        sa.Column("msp_sw_ver", sa.BigInteger(), nullable=False),
        sa.Column("config_a_bits", sa.BigInteger(), nullable=False),
        sa.Column("config_b_bits", sa.BigInteger(), nullable=False),
        sa.Column("pcba_sn", sa.BigInteger(), nullable=False),
        sa.Column("pcba_rev", sa.String(50), nullable=False),
        sa.Column(
            "pump_features",
            postgresql.JSONB(),
            nullable=False,
            server_default="{}",
        ),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        "ix_pump_hardware_info_user_id",
        "pump_hardware_info",
        ["user_id"],
        unique=True,
    )

    # -- tandem_upload_state --
    op.create_table(
        "tandem_upload_state",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
            unique=True,
        ),
        sa.Column("enabled", sa.Boolean(), nullable=False, server_default="false"),
        sa.Column(
            "upload_interval_minutes",
            sa.Integer(),
            nullable=False,
            server_default="15",
        ),
        sa.Column("last_upload_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("last_upload_status", sa.String(20), nullable=True),
        sa.Column("last_error", sa.Text(), nullable=True),
        sa.Column(
            "max_event_index_uploaded",
            sa.Integer(),
            nullable=False,
            server_default="0",
        ),
        sa.Column("tandem_access_token", sa.Text(), nullable=True),
        sa.Column("tandem_refresh_token", sa.Text(), nullable=True),
        sa.Column(
            "tandem_token_expires_at", sa.DateTime(timezone=True), nullable=True
        ),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        "ix_tandem_upload_state_user_id",
        "tandem_upload_state",
        ["user_id"],
        unique=True,
    )


def downgrade() -> None:
    op.drop_index("ix_tandem_upload_state_user_id", table_name="tandem_upload_state")
    op.drop_table("tandem_upload_state")

    op.drop_index("ix_pump_hardware_info_user_id", table_name="pump_hardware_info")
    op.drop_table("pump_hardware_info")

    op.drop_index("ix_pump_raw_events_user_uploaded", table_name="pump_raw_events")
    op.drop_index("ix_pump_raw_events_user_id", table_name="pump_raw_events")
    op.drop_table("pump_raw_events")
