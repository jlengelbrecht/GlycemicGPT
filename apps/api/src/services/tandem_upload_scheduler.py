"""Story 16.6: Background job for Tandem cloud uploads.

Called by the APScheduler in scheduler.py. Checks all users with
Tandem upload enabled and triggers uploads for those whose interval
has elapsed since their last upload.
"""

import asyncio
from datetime import UTC, datetime, timedelta

from sqlalchemy import select

from src.database import get_session_maker
from src.logging_config import get_logger
from src.models.tandem_upload_state import TandemUploadState
from src.services.tandem_upload import upload_to_tandem

logger = get_logger(__name__)

_running = False


async def run_tandem_cloud_uploads() -> None:
    """Check all users with Tandem upload enabled and trigger due uploads.

    This function is called by the APScheduler on a fixed interval (default 1 min).
    It only uploads for users whose personal upload_interval_minutes has elapsed.
    Guarded against concurrent execution via a module-level flag.
    """
    global _running  # noqa: PLW0603
    if _running:
        logger.debug("Tandem upload check already running, skipping")
        return
    _running = True
    try:
        await _do_tandem_cloud_uploads()
    finally:
        _running = False


async def _do_tandem_cloud_uploads() -> None:
    async with get_session_maker()() as db:
        result = await db.execute(
            select(TandemUploadState).where(TandemUploadState.enabled.is_(True))
        )
        states = list(result.scalars().all())

    if not states:
        return

    now = datetime.now(UTC)
    due_count = 0

    for state in states:
        interval = timedelta(minutes=state.upload_interval_minutes)
        last = state.last_upload_at
        if last and (now - last) < interval:
            continue

        due_count += 1
        try:
            async with get_session_maker()() as user_db:
                result = await upload_to_tandem(user_db, state.user_id)
                logger.info(
                    "Scheduled Tandem upload",
                    user_id=str(state.user_id),
                    events_uploaded=result.get("events_uploaded", 0),
                    status=result.get("status"),
                )
        except Exception:
            logger.error(
                "Scheduled Tandem upload failed",
                user_id=str(state.user_id),
                exc_info=True,
            )

        # Small delay between users
        await asyncio.sleep(1)

    if due_count > 0:
        logger.info("Tandem upload check completed", users_processed=due_count)
