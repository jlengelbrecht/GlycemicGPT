"""Story 10.2: Add display_name column to users table.

Revision ID: 027_add_display_name
Revises: 026_data_retention_configs
"""

import sqlalchemy as sa
from alembic import op

revision = "027_add_display_name"
down_revision = "026_data_retention_configs"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "users",
        sa.Column("display_name", sa.String(100), nullable=True),
    )


def downgrade() -> None:
    op.drop_column("users", "display_name")
