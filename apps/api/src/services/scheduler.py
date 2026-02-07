"""Story 3.2 & 3.4: Background job scheduler.

APScheduler-based background task scheduler for data sync jobs.
"""

import asyncio
from collections.abc import AsyncGenerator
from contextlib import asynccontextmanager

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.interval import IntervalTrigger
from sqlalchemy import select

from src.config import settings
from src.database import get_session_maker
from src.logging_config import get_logger
from src.models.integration import (
    IntegrationCredential,
    IntegrationStatus,
    IntegrationType,
)
from src.services.dexcom_sync import DexcomSyncError, sync_dexcom_for_user
from src.services.tandem_sync import TandemSyncError, sync_tandem_for_user

logger = get_logger(__name__)

# Global scheduler instance
scheduler: AsyncIOScheduler | None = None


async def sync_all_dexcom_users() -> None:
    """Sync Dexcom data for all users with configured credentials.

    This job runs on a schedule and syncs data for all users
    who have connected their Dexcom accounts.
    """
    logger.info("Starting scheduled Dexcom sync for all users")

    async with get_session_maker()() as db:
        # Find all users with active Dexcom integration
        result = await db.execute(
            select(IntegrationCredential).where(
                IntegrationCredential.integration_type == IntegrationType.DEXCOM,
                IntegrationCredential.status.in_(
                    [
                        IntegrationStatus.CONNECTED,
                        IntegrationStatus.ERROR,  # Retry errors
                    ]
                ),
            )
        )
        credentials = result.scalars().all()

        if not credentials:
            logger.info("No users with Dexcom integration to sync")
            return

        logger.info(
            "Found users for Dexcom sync",
            user_count=len(credentials),
        )

        # Sync each user
        success_count = 0
        error_count = 0

        for credential in credentials:
            try:
                # Create a new session for each user to isolate errors
                async with get_session_maker()() as user_db:
                    result = await sync_dexcom_for_user(user_db, credential.user_id)
                    logger.debug(
                        "Dexcom sync completed for user",
                        user_id=str(credential.user_id),
                        readings_fetched=result["readings_fetched"],
                        readings_stored=result["readings_stored"],
                    )
                    success_count += 1

            except DexcomSyncError as e:
                logger.warning(
                    "Scheduled Dexcom sync failed for user",
                    user_id=str(credential.user_id),
                    error=str(e),
                )
                error_count += 1

            except Exception as e:
                logger.error(
                    "Unexpected error in scheduled Dexcom sync",
                    user_id=str(credential.user_id),
                    error=str(e),
                )
                error_count += 1

            # Small delay between users to avoid rate limiting
            await asyncio.sleep(1)

    logger.info(
        "Scheduled Dexcom sync completed",
        success_count=success_count,
        error_count=error_count,
    )


async def sync_all_tandem_users() -> None:
    """Sync Tandem data for all users with configured credentials.

    This job runs on a schedule and syncs pump data for all users
    who have connected their Tandem t:connect accounts.
    """
    logger.info("Starting scheduled Tandem sync for all users")

    async with get_session_maker()() as db:
        # Find all users with active Tandem integration
        result = await db.execute(
            select(IntegrationCredential).where(
                IntegrationCredential.integration_type == IntegrationType.TANDEM,
                IntegrationCredential.status.in_(
                    [
                        IntegrationStatus.CONNECTED,
                        IntegrationStatus.ERROR,  # Retry errors
                    ]
                ),
            )
        )
        credentials = result.scalars().all()

        if not credentials:
            logger.info("No users with Tandem integration to sync")
            return

        logger.info(
            "Found users for Tandem sync",
            user_count=len(credentials),
        )

        # Sync each user
        success_count = 0
        error_count = 0

        for credential in credentials:
            try:
                # Create a new session for each user to isolate errors
                async with get_session_maker()() as user_db:
                    result = await sync_tandem_for_user(user_db, credential.user_id)
                    logger.info(
                        "Tandem sync completed for user",
                        user_id=str(credential.user_id),
                        events_fetched=result["events_fetched"],
                        events_stored=result["events_stored"],
                    )
                    success_count += 1

            except TandemSyncError as e:
                logger.warning(
                    "Scheduled Tandem sync failed for user",
                    user_id=str(credential.user_id),
                    error=str(e),
                )
                error_count += 1

            except Exception as e:
                logger.error(
                    "Unexpected error in scheduled Tandem sync",
                    user_id=str(credential.user_id),
                    error=str(e),
                )
                error_count += 1

            # Small delay between users to avoid rate limiting
            await asyncio.sleep(1)

    logger.info(
        "Scheduled Tandem sync completed",
        success_count=success_count,
        error_count=error_count,
    )


def start_scheduler() -> AsyncIOScheduler:
    """Start the background job scheduler.

    Returns:
        The started scheduler instance
    """
    global scheduler

    if scheduler is not None:
        logger.warning("Scheduler already running")
        return scheduler

    scheduler = AsyncIOScheduler()

    # Add Dexcom sync job if enabled
    if settings.dexcom_sync_enabled:
        scheduler.add_job(
            sync_all_dexcom_users,
            trigger=IntervalTrigger(minutes=settings.dexcom_sync_interval_minutes),
            id="dexcom_sync",
            name="Dexcom CGM Data Sync",
            replace_existing=True,
        )
        logger.info(
            "Scheduled Dexcom sync job",
            interval_minutes=settings.dexcom_sync_interval_minutes,
        )

    # Add Tandem sync job if enabled (Story 3.4)
    if settings.tandem_sync_enabled:
        scheduler.add_job(
            sync_all_tandem_users,
            trigger=IntervalTrigger(minutes=settings.tandem_sync_interval_minutes),
            id="tandem_sync",
            name="Tandem Pump Data Sync",
            replace_existing=True,
        )
        logger.info(
            "Scheduled Tandem sync job",
            interval_minutes=settings.tandem_sync_interval_minutes,
        )

    scheduler.start()
    logger.info("Background scheduler started")

    return scheduler


def stop_scheduler() -> None:
    """Stop the background job scheduler."""
    global scheduler

    if scheduler is not None:
        scheduler.shutdown(wait=False)
        scheduler = None
        logger.info("Background scheduler stopped")


def get_scheduler() -> AsyncIOScheduler | None:
    """Get the current scheduler instance.

    Returns:
        The scheduler instance or None if not started
    """
    return scheduler


@asynccontextmanager
async def scheduler_lifespan() -> AsyncGenerator[None, None]:
    """Async context manager for scheduler lifecycle.

    Use this in FastAPI lifespan to manage scheduler start/stop.
    """
    start_scheduler()
    try:
        yield
    finally:
        stop_scheduler()
