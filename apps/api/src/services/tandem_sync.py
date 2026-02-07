"""Story 3.4: Tandem Pump Data Sync Service.

Handles fetching pump data from Tandem t:connect API and storing them.
"""

import asyncio
import uuid
from datetime import datetime, timedelta, timezone

from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncSession
from tconnectsync.api.common import ApiException
from tconnectsync.api.tandemsource import TandemSourceApi

from src.config import settings
from src.core.encryption import decrypt_credential
from src.logging_config import get_logger
from src.models.integration import (
    IntegrationCredential,
    IntegrationStatus,
    IntegrationType,
)
from src.models.pump_data import PumpEvent, PumpEventType

logger = get_logger(__name__)

# Retry configuration
MAX_RETRIES = 3
RETRY_DELAY = 2  # seconds


class TandemSyncError(Exception):
    """Base exception for Tandem sync errors."""

    pass


class TandemNotConfiguredError(TandemSyncError):
    """Tandem integration is not configured for this user."""

    pass


class TandemAuthError(TandemSyncError):
    """Authentication failed with Tandem."""

    pass


class TandemConnectionError(TandemSyncError):
    """Connection to Tandem failed."""

    pass


def map_event_type(event_data: dict) -> tuple[PumpEventType, bool, str | None]:
    """Map tconnectsync event data to our PumpEventType.

    Args:
        event_data: Event dictionary from tconnectsync parser

    Returns:
        Tuple of (event_type, is_automated, control_iq_reason)
    """
    event_type_str = (event_data.get("type") or "").lower()

    # Check automation flags from tconnectsync
    is_automated = (
        event_data.get("isAutomated", False)
        or event_data.get("is_automated", False)
        or "auto" in event_type_str
    )

    control_iq_reason = None

    # Determine event type - order matters for specificity
    if "suspend" in event_type_str:
        if is_automated:
            control_iq_reason = "suspend"
        return PumpEventType.SUSPEND, is_automated, control_iq_reason

    if "resume" in event_type_str:
        return PumpEventType.RESUME, is_automated, control_iq_reason

    if "correction" in event_type_str:
        # Corrections are always automated (Control-IQ)
        return PumpEventType.CORRECTION, True, "correction"

    if "bolus" in event_type_str:
        # Check if it's an automated correction bolus
        if is_automated:
            return PumpEventType.CORRECTION, True, "correction"
        return PumpEventType.BOLUS, False, None

    if "basal" in event_type_str:
        if is_automated:
            control_iq_reason = "basal_adjustment"
        return PumpEventType.BASAL, is_automated, control_iq_reason

    # Default to bolus for unknown types
    logger.warning("Unknown event type, defaulting to BOLUS", event_type=event_type_str)
    return PumpEventType.BOLUS, is_automated, control_iq_reason


def fetch_with_retry(
    api: TandemSourceApi,
    start_date: datetime,
    end_date: datetime,
    max_retries: int = MAX_RETRIES,
):
    """Fetch events with retry logic for transient failures.

    Args:
        api: TandemSourceApi instance
        start_date: Start of date range
        end_date: End of date range
        max_retries: Maximum retry attempts

    Returns:
        Raw events from API

    Raises:
        ApiException: If all retries fail
    """
    import time

    last_error = None
    for attempt in range(max_retries):
        try:
            return api.get_events(start_date, end_date)
        except ApiException as e:
            last_error = e
            if attempt < max_retries - 1:
                logger.warning(
                    "Tandem API call failed, retrying",
                    attempt=attempt + 1,
                    max_retries=max_retries,
                    error=str(e),
                )
                time.sleep(RETRY_DELAY * (attempt + 1))
            else:
                raise
    raise last_error


async def sync_tandem_for_user(
    db: AsyncSession,
    user_id: uuid.UUID,
    hours_back: int | None = None,
) -> dict:
    """Sync Tandem pump data for a specific user.

    Args:
        db: Database session
        user_id: User ID to sync for
        hours_back: Hours of history to fetch (default from settings)

    Returns:
        Dict with sync results (events_fetched, events_stored, last_event)

    Raises:
        TandemNotConfiguredError: If integration is not configured
        TandemAuthError: If credentials are invalid
        TandemConnectionError: If connection fails
        TandemSyncError: For other sync errors
    """
    if hours_back is None:
        hours_back = getattr(settings, "tandem_sync_hours_back", 24)

    logger.info(
        "Starting Tandem sync for user",
        user_id=str(user_id),
        hours_back=hours_back,
    )

    # Get user's Tandem credentials
    result = await db.execute(
        select(IntegrationCredential).where(
            IntegrationCredential.user_id == user_id,
            IntegrationCredential.integration_type == IntegrationType.TANDEM,
        )
    )
    credential = result.scalar_one_or_none()

    if not credential:
        logger.warning("No Tandem credentials found for user", user_id=str(user_id))
        raise TandemNotConfiguredError("Tandem integration not configured")

    if credential.status == IntegrationStatus.DISCONNECTED:
        logger.warning("Tandem integration is disconnected", user_id=str(user_id))
        raise TandemNotConfiguredError("Tandem integration is disconnected")

    # Decrypt credentials
    try:
        username = decrypt_credential(credential.encrypted_username)
        password = decrypt_credential(credential.encrypted_password)
    except ValueError as e:
        logger.error(
            "Failed to decrypt Tandem credentials",
            user_id=str(user_id),
            error=str(e),
        )
        credential.status = IntegrationStatus.ERROR
        credential.last_error = "Credential decryption failed"
        await db.commit()
        raise TandemSyncError("Failed to decrypt credentials") from e

    # Get region from credential (default to US for backwards compatibility)
    region = getattr(credential, "region", "US") or "US"

    # Connect to Tandem
    try:
        api = TandemSourceApi(email=username, password=password, region=region)
    except ValueError as e:
        logger.warning(
            "Tandem invalid region",
            user_id=str(user_id),
            region=region,
            error=str(e),
        )
        credential.status = IntegrationStatus.ERROR
        credential.last_error = "Invalid region configuration"
        await db.commit()
        raise TandemAuthError("Invalid region") from e
    except ApiException as e:
        error_str = str(e).lower()
        if "login" in error_str or "credential" in error_str or "401" in error_str:
            logger.warning(
                "Tandem authentication failed",
                user_id=str(user_id),
                error=str(e),
            )
            credential.status = IntegrationStatus.ERROR
            credential.last_error = "Authentication failed - check credentials"
            await db.commit()
            raise TandemAuthError("Invalid Tandem credentials") from e
        logger.error(
            "Failed to connect to Tandem",
            user_id=str(user_id),
            error=str(e),
        )
        credential.status = IntegrationStatus.ERROR
        credential.last_error = f"Connection failed: {str(e)}"
        await db.commit()
        raise TandemConnectionError(f"Failed to connect: {str(e)}") from e
    except Exception as e:
        logger.error(
            "Unexpected error connecting to Tandem",
            user_id=str(user_id),
            error=str(e),
        )
        credential.status = IntegrationStatus.ERROR
        credential.last_error = f"Connection failed: {str(e)}"
        await db.commit()
        raise TandemConnectionError(f"Failed to connect: {str(e)}") from e

    # Calculate date range
    end_date = datetime.now(timezone.utc)
    start_date = end_date - timedelta(hours=hours_back)

    # Fetch events from Tandem with retry logic
    try:
        # Run synchronous API call in thread pool to avoid blocking
        raw_events = await asyncio.to_thread(
            fetch_with_retry, api, start_date, end_date
        )
    except ApiException as e:
        logger.warning(
            "Tandem API error during fetch",
            user_id=str(user_id),
            error=str(e),
        )
        credential.status = IntegrationStatus.ERROR
        credential.last_error = "API error during data fetch"
        await db.commit()
        raise TandemConnectionError("API error during fetch") from e
    except Exception as e:
        logger.error(
            "Failed to fetch Tandem events",
            user_id=str(user_id),
            error=str(e),
        )
        credential.status = IntegrationStatus.ERROR
        credential.last_error = f"Fetch failed: {str(e)}"
        await db.commit()
        raise TandemSyncError(f"Failed to fetch events: {str(e)}") from e

    # Parse and process events
    events = []
    if raw_events:
        # Handle different response structures from tconnectsync
        if isinstance(raw_events, dict):
            # May contain separate event types
            for event_type_key in ["basal", "bolus", "iob", "events"]:
                if event_type_key in raw_events:
                    items = raw_events[event_type_key]
                    if isinstance(items, list):
                        events.extend(items)
        elif isinstance(raw_events, list):
            events = raw_events

    if not events:
        logger.info("No new events from Tandem", user_id=str(user_id))
        credential.status = IntegrationStatus.CONNECTED
        credential.last_sync_at = datetime.now(timezone.utc)
        credential.last_error = None
        await db.commit()
        return {
            "events_fetched": 0,
            "events_stored": 0,
            "last_event": None,
        }

    # Store events (using upsert to handle duplicates)
    now = datetime.now(timezone.utc)
    stored_count = 0
    last_event = None

    for event_data in events:
        # Extract timestamp
        event_time = None
        for time_key in ["timestamp", "time", "datetime", "eventDateTime"]:
            if time_key in event_data:
                time_val = event_data[time_key]
                if isinstance(time_val, datetime):
                    event_time = time_val
                elif isinstance(time_val, str):
                    try:
                        event_time = datetime.fromisoformat(
                            time_val.replace("Z", "+00:00")
                        )
                    except ValueError:
                        pass
                break

        if not event_time:
            continue

        # Map event type
        event_type, is_automated, control_iq_reason = map_event_type(event_data)

        # Extract insulin units
        units = None
        for units_key in ["units", "insulin", "deliveredUnits", "value"]:
            if units_key in event_data:
                try:
                    units = float(event_data[units_key])
                    break
                except (ValueError, TypeError):
                    pass

        # Extract duration for basal
        duration_minutes = None
        if event_type == PumpEventType.BASAL:
            for dur_key in ["duration", "durationMinutes", "duration_minutes"]:
                if dur_key in event_data:
                    try:
                        duration_minutes = int(event_data[dur_key])
                        break
                    except (ValueError, TypeError):
                        pass

        # Extract context values
        iob = None
        for iob_key in ["iob", "insulinOnBoard", "insulin_on_board"]:
            if iob_key in event_data:
                try:
                    iob = float(event_data[iob_key])
                    break
                except (ValueError, TypeError):
                    pass

        cob = None
        for cob_key in ["cob", "carbsOnBoard", "carbs_on_board"]:
            if cob_key in event_data:
                try:
                    cob = float(event_data[cob_key])
                    break
                except (ValueError, TypeError):
                    pass

        bg = None
        for bg_key in ["bg", "glucose", "bloodGlucose", "blood_glucose"]:
            if bg_key in event_data:
                try:
                    bg = int(event_data[bg_key])
                    break
                except (ValueError, TypeError):
                    pass

        # Use PostgreSQL upsert (INSERT ON CONFLICT DO NOTHING)
        stmt = (
            insert(PumpEvent)
            .values(
                id=uuid.uuid4(),
                user_id=user_id,
                event_type=event_type,
                event_timestamp=event_time,
                units=units,
                duration_minutes=duration_minutes,
                is_automated=is_automated,
                control_iq_reason=control_iq_reason,
                iob_at_event=iob,
                cob_at_event=cob,
                bg_at_event=bg,
                received_at=now,
                source="tandem",
            )
            .on_conflict_do_nothing(
                index_elements=["user_id", "event_timestamp", "event_type"]
            )
        )

        result = await db.execute(stmt)
        if result.rowcount > 0:
            stored_count += 1

        # Track the most recent event
        if last_event is None or event_time > last_event["timestamp"]:
            last_event = {
                "event_type": event_type.value,
                "timestamp": event_time,
                "units": units,
                "is_automated": is_automated,
            }

    # Update integration status
    credential.status = IntegrationStatus.CONNECTED
    credential.last_sync_at = now
    credential.last_error = None
    await db.commit()

    logger.info(
        "Tandem sync completed",
        user_id=str(user_id),
        events_fetched=len(events),
        events_stored=stored_count,
        last_event_type=last_event["event_type"] if last_event else None,
    )

    return {
        "events_fetched": len(events),
        "events_stored": stored_count,
        "last_event": last_event,
    }


async def get_latest_pump_event(
    db: AsyncSession,
    user_id: uuid.UUID,
) -> PumpEvent | None:
    """Get the most recent pump event for a user.

    Args:
        db: Database session
        user_id: User ID

    Returns:
        Most recent PumpEvent or None
    """
    result = await db.execute(
        select(PumpEvent)
        .where(PumpEvent.user_id == user_id)
        .order_by(PumpEvent.event_timestamp.desc())
        .limit(1)
    )
    return result.scalar_one_or_none()


async def get_pump_events(
    db: AsyncSession,
    user_id: uuid.UUID,
    hours: int = 24,
    limit: int = 100,
    event_type: PumpEventType | None = None,
) -> list[PumpEvent]:
    """Get recent pump events for a user.

    Args:
        db: Database session
        user_id: User ID
        hours: Number of hours of history (default 24)
        limit: Maximum events to return (default 100)
        event_type: Optional filter by event type (e.g., PumpEventType.BASAL)

    Returns:
        List of PumpEvent objects, ordered by timestamp descending
    """
    cutoff = datetime.now(timezone.utc) - timedelta(hours=hours)

    query = select(PumpEvent).where(
        PumpEvent.user_id == user_id,
        PumpEvent.event_timestamp >= cutoff,
    )

    if event_type:
        query = query.where(PumpEvent.event_type == event_type)

    query = query.order_by(PumpEvent.event_timestamp.desc()).limit(limit)

    result = await db.execute(query)
    return list(result.scalars().all())
