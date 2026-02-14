"""Story 16.11: Device registration service.

Manages mobile device registration for alert delivery.
"""

import uuid
from datetime import UTC, datetime, timedelta

from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from src.logging_config import get_logger
from src.models.device_registration import DeviceRegistration

logger = get_logger(__name__)


async def register_device(
    db: AsyncSession,
    user_id: uuid.UUID,
    device_token: str,
    device_name: str,
    platform: str = "android",
) -> DeviceRegistration:
    """Register or update a device for alert delivery.

    Uses upsert semantics: if device_token already exists, updates
    last_seen_at and user association.

    Args:
        db: Database session.
        user_id: User's UUID.
        device_token: Unique device identifier.
        device_name: Human-readable device name.
        platform: Device platform (default: android).

    Returns:
        The registered DeviceRegistration.
    """
    from sqlalchemy.exc import IntegrityError

    now = datetime.now(UTC)

    # Try insert first, fall back to update on conflict (avoids TOCTOU race)
    device = DeviceRegistration(
        user_id=user_id,
        device_token=device_token,
        device_name=device_name,
        platform=platform,
        last_seen_at=now,
        created_at=now,
    )
    try:
        db.add(device)
        await db.flush()
    except IntegrityError:
        await db.rollback()
        # Token already exists, update instead
        result = await db.execute(
            select(DeviceRegistration).where(
                DeviceRegistration.device_token == device_token
            )
        )
        existing = result.scalar_one()
        existing.user_id = user_id
        existing.device_name = device_name
        existing.platform = platform
        existing.last_seen_at = now
        await db.commit()
        await db.refresh(existing)
        logger.info(
            "Device registration updated",
            user_id=str(user_id),
            device_token=device_token[:8] + "...",
        )
        return existing

    await db.commit()
    await db.refresh(device)
    logger.info(
        "Device registered",
        user_id=str(user_id),
        device_token=device_token[:8] + "...",
    )
    return device


async def unregister_device(
    db: AsyncSession,
    device_token: str,
    user_id: uuid.UUID | None = None,
) -> bool:
    """Remove a device registration.

    Args:
        db: Database session.
        device_token: Unique device identifier.
        user_id: If provided, only delete if the device belongs to this user.

    Returns:
        True if a device was removed, False if not found.
    """
    from sqlalchemy import and_

    conditions = [DeviceRegistration.device_token == device_token]
    if user_id is not None:
        conditions.append(DeviceRegistration.user_id == user_id)

    result = await db.execute(delete(DeviceRegistration).where(and_(*conditions)))
    await db.commit()
    removed = result.rowcount > 0
    if removed:
        logger.info(
            "Device unregistered",
            device_token=device_token[:8] + "...",
        )
    return removed


async def get_devices_for_user(
    db: AsyncSession,
    user_id: uuid.UUID,
) -> list[DeviceRegistration]:
    """List active devices for a user.

    Args:
        db: Database session.
        user_id: User's UUID.

    Returns:
        List of registered devices.
    """
    result = await db.execute(
        select(DeviceRegistration)
        .where(DeviceRegistration.user_id == user_id)
        .order_by(DeviceRegistration.last_seen_at.desc())
    )
    return list(result.scalars().all())


async def cleanup_stale_devices(
    db: AsyncSession,
    max_age_days: int = 30,
) -> int:
    """Remove devices not seen in the given number of days.

    Args:
        db: Database session.
        max_age_days: Maximum age in days before cleanup.

    Returns:
        Number of devices removed.
    """
    cutoff = datetime.now(UTC) - timedelta(days=max_age_days)
    result = await db.execute(
        delete(DeviceRegistration).where(DeviceRegistration.last_seen_at < cutoff)
    )
    await db.commit()
    count = result.rowcount
    if count > 0:
        logger.info("Cleaned up stale devices", count=count, max_age_days=max_age_days)
    return count
