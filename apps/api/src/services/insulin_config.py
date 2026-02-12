"""Insulin configuration service.

Manages user insulin type and DIA settings with get-or-create pattern.
"""

import uuid

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from src.logging_config import get_logger
from src.models.insulin_config import InsulinConfig
from src.schemas.insulin_config import InsulinConfigUpdate

logger = get_logger(__name__)


async def get_or_create_config(
    user_id: uuid.UUID,
    db: AsyncSession,
) -> InsulinConfig:
    """Get the user's insulin config, creating defaults if none exist.

    Args:
        user_id: User's UUID.
        db: Database session.

    Returns:
        The user's InsulinConfig record.
    """
    result = await db.execute(
        select(InsulinConfig).where(InsulinConfig.user_id == user_id)
    )
    config = result.scalar_one_or_none()

    if config is None:
        config = InsulinConfig(user_id=user_id)
        db.add(config)
        try:
            await db.commit()
        except IntegrityError:
            await db.rollback()
            result = await db.execute(
                select(InsulinConfig).where(InsulinConfig.user_id == user_id)
            )
            config = result.scalar_one()
            return config
        await db.refresh(config)

        logger.info(
            "Created default insulin config",
            user_id=str(user_id),
        )

    return config


async def update_config(
    user_id: uuid.UUID,
    updates: InsulinConfigUpdate,
    db: AsyncSession,
) -> InsulinConfig:
    """Update the user's insulin configuration.

    Only fields provided in the request are updated.

    Args:
        user_id: User's UUID.
        updates: Partial update with new config values.
        db: Database session.

    Returns:
        The updated InsulinConfig record.
    """
    config = await get_or_create_config(user_id, db)

    update_data = updates.model_dump(exclude_none=True)
    for field, value in update_data.items():
        setattr(config, field, value)

    await db.commit()
    await db.refresh(config)

    logger.info(
        "Updated insulin config",
        user_id=str(user_id),
        fields=list(update_data.keys()),
    )

    return config
