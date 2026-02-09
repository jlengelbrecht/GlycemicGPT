"""Story 7.1: Create telegram_links and telegram_verification_codes tables.

Revision ID: 020_telegram_links
Revises: 019_escalation_events
Create Date: 2026-02-09
"""

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "020_telegram_links"
down_revision = "019_escalation_events"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Create telegram_links table
    op.create_table(
        "telegram_links",
        sa.Column(
            "id",
            postgresql.UUID(as_uuid=True),
            primary_key=True,
            server_default=sa.text("gen_random_uuid()"),
        ),
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
            unique=True,
        ),
        sa.Column(
            "chat_id",
            sa.BigInteger(),
            nullable=False,
            unique=True,
        ),
        sa.Column(
            "username",
            sa.String(100),
            nullable=True,
        ),
        sa.Column(
            "is_verified",
            sa.Boolean(),
            nullable=False,
            server_default=sa.text("true"),
        ),
        sa.Column(
            "linked_at",
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
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
    )

    # Create indexes for telegram_links
    op.create_index(
        "ix_telegram_links_user_id",
        "telegram_links",
        ["user_id"],
    )
    op.create_index(
        "ix_telegram_links_chat_id",
        "telegram_links",
        ["chat_id"],
    )

    # Create telegram_verification_codes table
    op.create_table(
        "telegram_verification_codes",
        sa.Column(
            "id",
            postgresql.UUID(as_uuid=True),
            primary_key=True,
            server_default=sa.text("gen_random_uuid()"),
        ),
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
            unique=True,
        ),
        sa.Column(
            "code",
            sa.String(8),
            nullable=False,
            unique=True,
        ),
        sa.Column(
            "expires_at",
            sa.DateTime(timezone=True),
            nullable=False,
        ),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
    )


def downgrade() -> None:
    op.drop_table("telegram_verification_codes")
    op.drop_index("ix_telegram_links_chat_id", table_name="telegram_links")
    op.drop_index("ix_telegram_links_user_id", table_name="telegram_links")
    op.drop_table("telegram_links")
