"""Story 8.1: Create caregiver_invitations table.

Revision ID: 022_caregiver_invitations
Revises: 021_caregiver_links
Create Date: 2026-02-09
"""

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "022_caregiver_invitations"
down_revision = "021_caregiver_links"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "caregiver_invitations",
        sa.Column(
            "id",
            postgresql.UUID(as_uuid=True),
            primary_key=True,
            server_default=sa.text("gen_random_uuid()"),
        ),
        sa.Column(
            "patient_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "token",
            sa.String(64),
            nullable=False,
            unique=True,
        ),
        sa.Column(
            "status",
            sa.String(16),
            nullable=False,
            server_default="pending",
        ),
        sa.Column(
            "expires_at",
            sa.DateTime(timezone=True),
            nullable=False,
        ),
        sa.Column(
            "accepted_by",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="SET NULL"),
            nullable=True,
        ),
        sa.Column(
            "accepted_at",
            sa.DateTime(timezone=True),
            nullable=True,
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
    )

    op.create_index(
        "ix_caregiver_invitations_patient_id",
        "caregiver_invitations",
        ["patient_id"],
    )
    op.create_index(
        "ix_caregiver_invitations_token",
        "caregiver_invitations",
        ["token"],
        unique=True,
    )
    op.create_index(
        "ix_caregiver_invitations_status",
        "caregiver_invitations",
        ["status"],
    )


def downgrade() -> None:
    op.drop_index(
        "ix_caregiver_invitations_status",
        table_name="caregiver_invitations",
    )
    op.drop_index(
        "ix_caregiver_invitations_token",
        table_name="caregiver_invitations",
    )
    op.drop_index(
        "ix_caregiver_invitations_patient_id",
        table_name="caregiver_invitations",
    )
    op.drop_table("caregiver_invitations")
