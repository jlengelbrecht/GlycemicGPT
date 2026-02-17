"""Add tandem_pumper_id column to tandem_upload_state.

Caches the Tandem pumperId (from JWT claims) so it can be included
in upload payloads even when using a cached access token.

Revision ID: 034_add_tandem_pumper_id
Revises: 033_add_device_registrations
"""

import sqlalchemy as sa
from alembic import op

revision = "034_add_tandem_pumper_id"
down_revision = "033_add_device_registrations"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "tandem_upload_state",
        sa.Column("tandem_pumper_id", sa.String(100), nullable=True),
    )


def downgrade() -> None:
    op.drop_column("tandem_upload_state", "tandem_pumper_id")
