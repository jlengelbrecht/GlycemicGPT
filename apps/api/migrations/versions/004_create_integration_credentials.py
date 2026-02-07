"""Story 3.1: Create integration_credentials table.

Revision ID: 004
Revises: 003
Create Date: 2026-02-06

"""

from collections.abc import Sequence

import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

from alembic import op

# revision identifiers, used by Alembic.
revision: str = "004_integrations"
down_revision: str | None = "003_users"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    # Create enum types for integration
    integration_type = postgresql.ENUM(
        "dexcom", "tandem",
        name="integrationtype",
    )
    integration_type.create(op.get_bind())

    integration_status = postgresql.ENUM(
        "pending", "connected", "error", "disconnected",
        name="integrationstatus",
    )
    integration_status.create(op.get_bind())

    # Create integration_credentials table
    op.create_table(
        "integration_credentials",
        sa.Column("id", sa.UUID(), nullable=False),
        sa.Column("user_id", sa.UUID(), nullable=False),
        sa.Column(
            "integration_type",
            postgresql.ENUM(
                "dexcom", "tandem",
                name="integrationtype",
                create_type=False,
            ),
            nullable=False,
        ),
        sa.Column("encrypted_username", sa.Text(), nullable=False),
        sa.Column("encrypted_password", sa.Text(), nullable=False),
        sa.Column(
            "status",
            postgresql.ENUM(
                "pending", "connected", "error", "disconnected",
                name="integrationstatus",
                create_type=False,
            ),
            nullable=False,
            server_default="pending",
        ),
        sa.Column("last_sync_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("last_error", sa.Text(), nullable=True),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(
            ["user_id"],
            ["users.id"],
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("user_id", "integration_type", name="uq_user_integration"),
    )

    # Create index on user_id for faster lookups
    op.create_index(
        "ix_integration_credentials_user_id",
        "integration_credentials",
        ["user_id"],
    )


def downgrade() -> None:
    op.drop_index("ix_integration_credentials_user_id")
    op.drop_table("integration_credentials")

    # Drop enum types
    op.execute("DROP TYPE IF EXISTS integrationstatus")
    op.execute("DROP TYPE IF EXISTS integrationtype")
