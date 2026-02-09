"""Story 7.6: Create caregiver_links table.

Revision ID: 021_caregiver_links
Revises: 020_telegram_links
Create Date: 2026-02-09
"""

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "021_caregiver_links"
down_revision = "020_telegram_links"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "caregiver_links",
        sa.Column(
            "id",
            postgresql.UUID(as_uuid=True),
            primary_key=True,
            server_default=sa.text("gen_random_uuid()"),
        ),
        sa.Column(
            "caregiver_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "patient_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
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
        sa.UniqueConstraint(
            "caregiver_id", "patient_id", name="uq_caregiver_patient"
        ),
        sa.CheckConstraint(
            "caregiver_id != patient_id", name="ck_no_self_link"
        ),
    )

    op.create_index(
        "ix_caregiver_links_caregiver_id",
        "caregiver_links",
        ["caregiver_id"],
    )
    op.create_index(
        "ix_caregiver_links_patient_id",
        "caregiver_links",
        ["patient_id"],
    )


def downgrade() -> None:
    op.drop_index(
        "ix_caregiver_links_patient_id", table_name="caregiver_links"
    )
    op.drop_index(
        "ix_caregiver_links_caregiver_id", table_name="caregiver_links"
    )
    op.drop_table("caregiver_links")
