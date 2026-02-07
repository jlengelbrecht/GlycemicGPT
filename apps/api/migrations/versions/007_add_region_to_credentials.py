"""Story 3.4 Review: Add region field to integration_credentials.

Revision ID: 007
Revises: 006
Create Date: 2026-02-06

"""

from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

# revision identifiers, used by Alembic.
revision: str = "007_region"
down_revision: str | None = "006_pump_events"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    # Add region column with default 'US' for existing records
    op.add_column(
        "integration_credentials",
        sa.Column("region", sa.String(10), nullable=False, server_default="US"),
    )


def downgrade() -> None:
    op.drop_column("integration_credentials", "region")
