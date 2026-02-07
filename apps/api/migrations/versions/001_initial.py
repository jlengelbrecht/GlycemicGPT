"""Initial migration - baseline.

Revision ID: 001_initial
Revises:
Create Date: 2026-02-06

This is the initial baseline migration. It doesn't create any tables
since no models are defined yet, but it establishes the migration
history in the alembic_version table.
"""

from typing import Sequence, Union

# revision identifiers, used by Alembic.
revision: str = "001_initial"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Initial migration - no tables yet."""
    pass


def downgrade() -> None:
    """Revert initial migration."""
    pass
