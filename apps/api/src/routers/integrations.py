"""Story 3.1, 3.2, 3.3 & 3.4: Integration credentials and data sync router.

API endpoints for managing third-party integrations (Dexcom, Tandem) and data sync.
"""

from datetime import UTC, datetime

from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydexcom import Dexcom
from pydexcom import errors as dexcom_errors
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession
from tconnectsync.api.common import ApiException
from tconnectsync.api.tandemsource import TandemSourceApi

from src.core.auth import DiabeticOrAdminUser
from src.core.encryption import encrypt_credential
from src.database import get_db
from src.logging_config import get_logger
from src.models.glucose import GlucoseReading
from src.models.integration import (
    IntegrationCredential,
    IntegrationStatus,
    IntegrationType,
)
from src.models.pump_data import PumpEvent
from src.schemas.auth import ErrorResponse
from src.schemas.glucose import (
    CurrentGlucoseResponse,
    GlucoseHistoryResponse,
    GlucoseReadingResponse,
    SyncResponse,
    SyncStatusResponse,
)
from src.schemas.integration import (
    DexcomCredentialsRequest,
    IntegrationConnectResponse,
    IntegrationDisconnectResponse,
    IntegrationListResponse,
    IntegrationResponse,
    TandemCredentialsRequest,
)
from src.schemas.pump import (
    ControlIQActivityResponse,
    IoBProjectionResponse,
    PumpEventResponse,
    TandemSyncResponse,
    TandemSyncStatusResponse,
)
from src.services.dexcom_sync import (
    DexcomAuthError,
    DexcomConnectionError,
    DexcomSyncError,
    get_glucose_readings,
    get_latest_glucose_reading,
    sync_dexcom_for_user,
)
from src.services.iob_projection import get_iob_projection, get_user_dia
from src.services.tandem_sync import (
    TandemAuthError,
    TandemConnectionError,
    TandemNotConfiguredError,
    TandemSyncError,
    get_control_iq_activity,
    get_latest_pump_event,
    sync_tandem_for_user,
)

logger = get_logger(__name__)

router = APIRouter(prefix="/api/integrations", tags=["integrations"])


def validate_dexcom_credentials(
    username: str, password: str
) -> tuple[bool, str | None]:
    """Validate Dexcom Share credentials by attempting to connect.

    Args:
        username: Dexcom Share email
        password: Dexcom Share password

    Returns:
        Tuple of (success, error_message)
    """
    try:
        # Try to connect to Dexcom - this validates credentials
        dexcom = Dexcom(username=username, password=password)
        # Try to get glucose readings to confirm connection works
        _ = dexcom.get_current_glucose_reading()
        return True, None
    except dexcom_errors.AccountError as e:
        logger.warning(
            "Dexcom credential validation failed - account error",
            error=str(e),
        )
        return (
            False,
            "Invalid Dexcom credentials. Please check your email and password.",
        )
    except dexcom_errors.SessionError as e:
        logger.warning(
            "Dexcom credential validation failed - session error",
            error=str(e),
        )
        return False, "Unable to connect to Dexcom. Please try again later."
    except Exception as e:
        logger.error(
            "Dexcom credential validation failed - unexpected error",
            error=str(e),
        )
        return (
            False,
            "An error occurred while validating credentials. Please try again.",
        )


def validate_tandem_credentials(
    username: str, password: str, region: str = "US"
) -> tuple[bool, str | None]:
    """Validate Tandem t:connect credentials by attempting to connect.

    Args:
        username: Tandem t:connect email
        password: Tandem t:connect password
        region: Account region ('US' or 'EU')

    Returns:
        Tuple of (success, error_message)
    """
    try:
        # Try to connect to Tandem - this validates credentials via login()
        # TandemSourceApi calls login() in __init__, so instantiation validates
        _api = TandemSourceApi(email=username, password=password, region=region)
        return True, None
    except ValueError as e:
        # Invalid region
        logger.warning(
            "Tandem credential validation failed - invalid region",
            error=str(e),
        )
        return False, f"Invalid region: {region}. Must be 'US' or 'EU'."
    except ApiException as e:
        logger.warning(
            "Tandem credential validation failed - API error",
            error=str(e),
        )
        # Check for specific error messages
        error_str = str(e).lower()
        if "login" in error_str or "credential" in error_str or "401" in error_str:
            return (
                False,
                "Invalid Tandem credentials. Please check your email and password.",
            )
        return False, "Unable to connect to Tandem t:connect. Please try again later."
    except Exception as e:
        logger.error(
            "Tandem credential validation failed - unexpected error",
            error=str(e),
        )
        return (
            False,
            "An error occurred while validating credentials. Please try again.",
        )


@router.get(
    "",
    response_model=IntegrationListResponse,
    responses={
        200: {"description": "List of integrations"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
        403: {"model": ErrorResponse, "description": "Permission denied"},
    },
)
async def list_integrations(
    current_user: DiabeticOrAdminUser,
    db: AsyncSession = Depends(get_db),
) -> IntegrationListResponse:
    """List all integrations for the current user.

    Returns the status of all configured integrations.
    """
    result = await db.execute(
        select(IntegrationCredential).where(
            IntegrationCredential.user_id == current_user.id
        )
    )
    credentials = result.scalars().all()

    return IntegrationListResponse(
        integrations=[IntegrationResponse.model_validate(cred) for cred in credentials]
    )


@router.post(
    "/dexcom",
    response_model=IntegrationConnectResponse,
    status_code=status.HTTP_201_CREATED,
    responses={
        201: {"description": "Dexcom connected successfully"},
        400: {"model": ErrorResponse, "description": "Invalid credentials"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
        403: {"model": ErrorResponse, "description": "Permission denied"},
    },
)
async def connect_dexcom(
    request: DexcomCredentialsRequest,
    current_user: DiabeticOrAdminUser,
    db: AsyncSession = Depends(get_db),
) -> IntegrationConnectResponse:
    """Connect Dexcom Share account.

    Validates the provided credentials and stores them encrypted
    in the database. If credentials already exist, they are updated.
    """
    # Validate credentials first
    is_valid, error_message = validate_dexcom_credentials(
        request.username,
        request.password,
    )

    if not is_valid:
        logger.warning(
            "Dexcom connection failed",
            user_id=str(current_user.id),
            error=error_message,
        )
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=error_message,
        )

    # Check if integration already exists
    result = await db.execute(
        select(IntegrationCredential).where(
            IntegrationCredential.user_id == current_user.id,
            IntegrationCredential.integration_type == IntegrationType.DEXCOM,
        )
    )
    existing = result.scalar_one_or_none()

    if existing:
        # Update existing credentials
        existing.encrypted_username = encrypt_credential(request.username)
        existing.encrypted_password = encrypt_credential(request.password)
        existing.status = IntegrationStatus.CONNECTED
        existing.last_error = None
        existing.updated_at = datetime.now(UTC)
        credential = existing
    else:
        # Create new credential
        credential = IntegrationCredential(
            user_id=current_user.id,
            integration_type=IntegrationType.DEXCOM,
            encrypted_username=encrypt_credential(request.username),
            encrypted_password=encrypt_credential(request.password),
            status=IntegrationStatus.CONNECTED,
        )
        db.add(credential)

    await db.commit()
    await db.refresh(credential)

    logger.info(
        "Dexcom connected successfully",
        user_id=str(current_user.id),
        integration_type="dexcom",
    )

    return IntegrationConnectResponse(
        message="Dexcom connected successfully",
        integration=IntegrationResponse.model_validate(credential),
    )


@router.delete(
    "/dexcom",
    response_model=IntegrationDisconnectResponse,
    responses={
        200: {"description": "Dexcom disconnected"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
        403: {"model": ErrorResponse, "description": "Permission denied"},
        404: {"model": ErrorResponse, "description": "Integration not found"},
    },
)
async def disconnect_dexcom(
    current_user: DiabeticOrAdminUser,
    db: AsyncSession = Depends(get_db),
) -> IntegrationDisconnectResponse:
    """Disconnect Dexcom Share account.

    Removes the stored credentials and marks the integration as disconnected.
    """
    result = await db.execute(
        select(IntegrationCredential).where(
            IntegrationCredential.user_id == current_user.id,
            IntegrationCredential.integration_type == IntegrationType.DEXCOM,
        )
    )
    credential = result.scalar_one_or_none()

    if not credential:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Dexcom integration not found",
        )

    await db.delete(credential)
    await db.commit()

    logger.info(
        "Dexcom disconnected",
        user_id=str(current_user.id),
        integration_type="dexcom",
    )

    return IntegrationDisconnectResponse(message="Dexcom disconnected successfully")


@router.get(
    "/dexcom/status",
    response_model=IntegrationResponse,
    responses={
        200: {"description": "Dexcom integration status"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
        403: {"model": ErrorResponse, "description": "Permission denied"},
        404: {"model": ErrorResponse, "description": "Integration not found"},
    },
)
async def get_dexcom_status(
    current_user: DiabeticOrAdminUser,
    db: AsyncSession = Depends(get_db),
) -> IntegrationResponse:
    """Get the current Dexcom integration status."""
    result = await db.execute(
        select(IntegrationCredential).where(
            IntegrationCredential.user_id == current_user.id,
            IntegrationCredential.integration_type == IntegrationType.DEXCOM,
        )
    )
    credential = result.scalar_one_or_none()

    if not credential:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Dexcom integration not found",
        )

    return IntegrationResponse.model_validate(credential)


# ============================================================================
# Story 3.3: Tandem t:connect Integration Endpoints
# ============================================================================


@router.post(
    "/tandem",
    response_model=IntegrationConnectResponse,
    status_code=status.HTTP_201_CREATED,
    responses={
        201: {"description": "Tandem t:connect connected successfully"},
        400: {"model": ErrorResponse, "description": "Invalid credentials"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
        403: {"model": ErrorResponse, "description": "Permission denied"},
    },
)
async def connect_tandem(
    request: TandemCredentialsRequest,
    current_user: DiabeticOrAdminUser,
    db: AsyncSession = Depends(get_db),
) -> IntegrationConnectResponse:
    """Connect Tandem t:connect account.

    Validates the provided credentials and stores them encrypted
    in the database. If credentials already exist, they are updated.
    """
    # Validate credentials first (with region)
    is_valid, error_message = validate_tandem_credentials(
        request.username,
        request.password,
        request.region,
    )

    if not is_valid:
        logger.warning(
            "Tandem connection failed",
            user_id=str(current_user.id),
            error=error_message,
        )
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=error_message,
        )

    # Check if integration already exists
    result = await db.execute(
        select(IntegrationCredential).where(
            IntegrationCredential.user_id == current_user.id,
            IntegrationCredential.integration_type == IntegrationType.TANDEM,
        )
    )
    existing = result.scalar_one_or_none()

    if existing:
        # Update existing credentials
        existing.encrypted_username = encrypt_credential(request.username)
        existing.encrypted_password = encrypt_credential(request.password)
        existing.region = request.region
        existing.status = IntegrationStatus.CONNECTED
        existing.last_error = None
        existing.updated_at = datetime.now(UTC)
        credential = existing
    else:
        # Create new credential
        credential = IntegrationCredential(
            user_id=current_user.id,
            integration_type=IntegrationType.TANDEM,
            encrypted_username=encrypt_credential(request.username),
            encrypted_password=encrypt_credential(request.password),
            region=request.region,
            status=IntegrationStatus.CONNECTED,
        )
        db.add(credential)

    await db.commit()
    await db.refresh(credential)

    logger.info(
        "Tandem t:connect connected successfully",
        user_id=str(current_user.id),
        integration_type="tandem",
    )

    return IntegrationConnectResponse(
        message="Tandem t:connect connected successfully",
        integration=IntegrationResponse.model_validate(credential),
    )


@router.delete(
    "/tandem",
    response_model=IntegrationDisconnectResponse,
    responses={
        200: {"description": "Tandem disconnected"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
        403: {"model": ErrorResponse, "description": "Permission denied"},
        404: {"model": ErrorResponse, "description": "Integration not found"},
    },
)
async def disconnect_tandem(
    current_user: DiabeticOrAdminUser,
    db: AsyncSession = Depends(get_db),
) -> IntegrationDisconnectResponse:
    """Disconnect Tandem t:connect account.

    Removes the stored credentials and marks the integration as disconnected.
    """
    result = await db.execute(
        select(IntegrationCredential).where(
            IntegrationCredential.user_id == current_user.id,
            IntegrationCredential.integration_type == IntegrationType.TANDEM,
        )
    )
    credential = result.scalar_one_or_none()

    if not credential:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Tandem integration not found",
        )

    await db.delete(credential)
    await db.commit()

    logger.info(
        "Tandem disconnected",
        user_id=str(current_user.id),
        integration_type="tandem",
    )

    return IntegrationDisconnectResponse(
        message="Tandem t:connect disconnected successfully"
    )


@router.get(
    "/tandem/status",
    response_model=IntegrationResponse,
    responses={
        200: {"description": "Tandem integration status"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
        403: {"model": ErrorResponse, "description": "Permission denied"},
        404: {"model": ErrorResponse, "description": "Integration not found"},
    },
)
async def get_tandem_status(
    current_user: DiabeticOrAdminUser,
    db: AsyncSession = Depends(get_db),
) -> IntegrationResponse:
    """Get the current Tandem t:connect integration status."""
    result = await db.execute(
        select(IntegrationCredential).where(
            IntegrationCredential.user_id == current_user.id,
            IntegrationCredential.integration_type == IntegrationType.TANDEM,
        )
    )
    credential = result.scalar_one_or_none()

    if not credential:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Tandem integration not found",
        )

    return IntegrationResponse.model_validate(credential)


# ============================================================================
# Story 3.2: Dexcom CGM Data Sync Endpoints
# ============================================================================


@router.post(
    "/dexcom/sync",
    response_model=SyncResponse,
    responses={
        200: {"description": "Sync completed"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
        403: {"model": ErrorResponse, "description": "Permission denied"},
        404: {"model": ErrorResponse, "description": "Dexcom not configured"},
        503: {"model": ErrorResponse, "description": "Dexcom service unavailable"},
    },
)
async def sync_dexcom_data(
    current_user: DiabeticOrAdminUser,
    db: AsyncSession = Depends(get_db),
) -> SyncResponse:
    """Manually trigger a Dexcom data sync.

    Fetches the latest glucose readings from Dexcom Share API
    and stores them in the database.
    """
    try:
        result = await sync_dexcom_for_user(db, current_user.id)

        last_reading = None
        if result["last_reading"]:
            last_reading = GlucoseReadingResponse(
                value=result["last_reading"]["value"],
                reading_timestamp=result["last_reading"]["timestamp"],
                trend=result["last_reading"]["trend"],
                trend_rate=None,
                received_at=datetime.now(UTC),
                source="dexcom",
            )

        return SyncResponse(
            message="Sync completed successfully",
            readings_fetched=result["readings_fetched"],
            readings_stored=result["readings_stored"],
            last_reading=last_reading,
        )

    except DexcomAuthError as e:
        logger.warning(
            "Dexcom sync failed - auth error",
            user_id=str(current_user.id),
            error=str(e),
        )
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid Dexcom credentials. Please reconnect your account.",
        ) from e

    except DexcomConnectionError as e:
        logger.warning(
            "Dexcom sync failed - connection error",
            user_id=str(current_user.id),
            error=str(e),
        )
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Unable to connect to Dexcom. Please try again later.",
        ) from e

    except DexcomSyncError as e:
        logger.error(
            "Dexcom sync failed",
            user_id=str(current_user.id),
            error=str(e),
        )
        if "not configured" in str(e).lower():
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Dexcom integration not configured",
            ) from e
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Sync failed: {str(e)}",
        ) from e


@router.get(
    "/dexcom/sync/status",
    response_model=SyncStatusResponse,
    responses={
        200: {"description": "Sync status"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
        403: {"model": ErrorResponse, "description": "Permission denied"},
    },
)
async def get_sync_status(
    current_user: DiabeticOrAdminUser,
    db: AsyncSession = Depends(get_db),
) -> SyncStatusResponse:
    """Get the current Dexcom sync status.

    Returns the integration status, last sync time, and latest reading.
    """
    # Get integration status
    result = await db.execute(
        select(IntegrationCredential).where(
            IntegrationCredential.user_id == current_user.id,
            IntegrationCredential.integration_type == IntegrationType.DEXCOM,
        )
    )
    credential = result.scalar_one_or_none()

    # Count readings
    count_result = await db.execute(
        select(func.count(GlucoseReading.id)).where(
            GlucoseReading.user_id == current_user.id
        )
    )
    readings_count = count_result.scalar() or 0

    # Get latest reading
    latest = await get_latest_glucose_reading(db, current_user.id)
    latest_response = None
    if latest:
        latest_response = GlucoseReadingResponse.model_validate(latest)

    return SyncStatusResponse(
        integration_status=credential.status.value if credential else "not_configured",
        last_sync_at=credential.last_sync_at if credential else None,
        last_error=credential.last_error if credential else None,
        readings_available=readings_count,
        latest_reading=latest_response,
    )


@router.get(
    "/glucose/current",
    response_model=CurrentGlucoseResponse,
    responses={
        200: {"description": "Current glucose reading"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
        403: {"model": ErrorResponse, "description": "Permission denied"},
        404: {"model": ErrorResponse, "description": "No readings available"},
    },
)
async def get_current_glucose(
    current_user: DiabeticOrAdminUser,
    db: AsyncSession = Depends(get_db),
) -> CurrentGlucoseResponse:
    """Get the current (most recent) glucose reading.

    Returns the latest glucose value with trend and staleness indicator.
    """
    latest = await get_latest_glucose_reading(db, current_user.id)

    if not latest:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="No glucose readings available. Please sync with Dexcom first.",
        )

    now = datetime.now(UTC)
    reading_time = latest.reading_timestamp
    if reading_time.tzinfo is None:
        reading_time = reading_time.replace(tzinfo=UTC)

    minutes_ago = int((now - reading_time).total_seconds() / 60)
    is_stale = minutes_ago > 10

    return CurrentGlucoseResponse(
        value=latest.value,
        trend=latest.trend,
        trend_rate=latest.trend_rate,
        reading_timestamp=latest.reading_timestamp,
        minutes_ago=minutes_ago,
        is_stale=is_stale,
    )


@router.get(
    "/glucose/history",
    response_model=GlucoseHistoryResponse,
    responses={
        200: {"description": "Glucose reading history"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
        403: {"model": ErrorResponse, "description": "Permission denied"},
    },
)
async def get_glucose_history(
    current_user: DiabeticOrAdminUser,
    db: AsyncSession = Depends(get_db),
    minutes: int = Query(default=180, ge=5, le=1440, description="Minutes of history"),
    limit: int = Query(default=36, ge=1, le=288, description="Max readings to return"),
) -> GlucoseHistoryResponse:
    """Get glucose reading history.

    Returns recent glucose readings for the specified time period.
    Default is 3 hours (180 minutes), max is 24 hours (1440 minutes).
    """
    readings = await get_glucose_readings(
        db, current_user.id, minutes=minutes, limit=limit
    )

    return GlucoseHistoryResponse(
        readings=[GlucoseReadingResponse.model_validate(r) for r in readings],
        count=len(readings),
    )


# ============================================================================
# Story 3.4: Tandem Pump Data Sync Endpoints
# ============================================================================


@router.post(
    "/tandem/sync",
    response_model=TandemSyncResponse,
    responses={
        200: {"description": "Sync completed"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
        403: {"model": ErrorResponse, "description": "Permission denied"},
        404: {"model": ErrorResponse, "description": "Tandem not configured"},
        503: {"model": ErrorResponse, "description": "Tandem service unavailable"},
    },
)
async def sync_tandem_data(
    current_user: DiabeticOrAdminUser,
    db: AsyncSession = Depends(get_db),
) -> TandemSyncResponse:
    """Manually trigger a Tandem pump data sync.

    Fetches the latest pump events from Tandem t:connect API
    and stores them in the database.
    """
    try:
        result = await sync_tandem_for_user(db, current_user.id)

        last_event = None
        if result["last_event"]:
            # Create a minimal response for the last event
            last_event = PumpEventResponse(
                event_type=result["last_event"]["event_type"],
                event_timestamp=result["last_event"]["timestamp"],
                units=result["last_event"]["units"],
                is_automated=result["last_event"]["is_automated"],
                received_at=datetime.now(UTC),
                source="tandem",
            )

        return TandemSyncResponse(
            message="Sync completed successfully",
            events_fetched=result["events_fetched"],
            events_stored=result["events_stored"],
            profiles_stored=result.get("profiles_stored", 0),
            last_event=last_event,
        )

    except TandemAuthError as e:
        logger.warning(
            "Tandem sync failed - auth error",
            user_id=str(current_user.id),
            error=str(e),
        )
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid Tandem credentials. Please reconnect your account.",
        ) from e

    except TandemConnectionError as e:
        logger.warning(
            "Tandem sync failed - connection error",
            user_id=str(current_user.id),
            error=str(e),
        )
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Unable to connect to Tandem. Please try again later.",
        ) from e

    except TandemNotConfiguredError as e:
        logger.warning(
            "Tandem sync failed - not configured",
            user_id=str(current_user.id),
            error=str(e),
        )
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Tandem integration not configured",
        ) from e

    except TandemSyncError as e:
        logger.error(
            "Tandem sync failed",
            user_id=str(current_user.id),
            error=str(e),
        )
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Sync failed: {str(e)}",
        ) from e


@router.get(
    "/tandem/sync/status",
    response_model=TandemSyncStatusResponse,
    responses={
        200: {"description": "Sync status"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
        403: {"model": ErrorResponse, "description": "Permission denied"},
    },
)
async def get_tandem_sync_status(
    current_user: DiabeticOrAdminUser,
    db: AsyncSession = Depends(get_db),
) -> TandemSyncStatusResponse:
    """Get the current Tandem sync status.

    Returns the integration status, last sync time, and latest event.
    """
    # Get integration status
    result = await db.execute(
        select(IntegrationCredential).where(
            IntegrationCredential.user_id == current_user.id,
            IntegrationCredential.integration_type == IntegrationType.TANDEM,
        )
    )
    credential = result.scalar_one_or_none()

    # Count events
    count_result = await db.execute(
        select(func.count(PumpEvent.id)).where(PumpEvent.user_id == current_user.id)
    )
    events_count = count_result.scalar() or 0

    # Get latest event
    latest = await get_latest_pump_event(db, current_user.id)
    latest_response = None
    if latest:
        latest_response = PumpEventResponse.model_validate(latest)

    return TandemSyncStatusResponse(
        integration_status=credential.status.value if credential else "not_configured",
        last_sync_at=credential.last_sync_at if credential else None,
        last_error=credential.last_error if credential else None,
        events_available=events_count,
        latest_event=latest_response,
    )


@router.get(
    "/tandem/control-iq/activity",
    response_model=ControlIQActivityResponse,
    responses={
        200: {"description": "Control-IQ activity summary"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
        403: {"model": ErrorResponse, "description": "Permission denied"},
    },
)
async def get_control_iq_activity_summary(
    current_user: DiabeticOrAdminUser,
    db: AsyncSession = Depends(get_db),
    hours: int = Query(
        default=24, ge=1, le=168, description="Hours of history to analyze"
    ),
) -> ControlIQActivityResponse:
    """Get a summary of Control-IQ activity (Story 3.5).

    This endpoint provides aggregated metrics about Control-IQ automated actions,
    including:
    - Automatic correction boluses
    - Basal rate adjustments (increases and decreases)
    - Automated insulin suspends
    - Activity mode usage (Sleep, Exercise, Standard)

    This data helps AI analysis focus on what Control-IQ cannot adjust
    (carb ratios, correction factors) rather than what it's already handling.

    Args:
        hours: Number of hours of history to analyze (1-168, default 24)

    Returns:
        ControlIQActivityResponse with aggregated metrics
    """
    activity = await get_control_iq_activity(db, current_user.id, hours=hours)

    return ControlIQActivityResponse(
        total_events=activity.total_events,
        automated_events=activity.automated_events,
        manual_events=activity.manual_events,
        correction_count=activity.correction_count,
        total_correction_units=activity.total_correction_units,
        basal_increase_count=activity.basal_increase_count,
        basal_decrease_count=activity.basal_decrease_count,
        avg_basal_adjustment_pct=activity.avg_basal_adjustment_pct,
        suspend_count=activity.suspend_count,
        automated_suspend_count=activity.automated_suspend_count,
        sleep_mode_events=activity.sleep_mode_events,
        exercise_mode_events=activity.exercise_mode_events,
        standard_mode_events=activity.standard_mode_events,
        start_time=activity.start_time,
        end_time=activity.end_time,
        hours_analyzed=hours,
    )


@router.get(
    "/tandem/iob/projection",
    response_model=IoBProjectionResponse,
    responses={
        200: {"description": "IoB projection data"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
        403: {"model": ErrorResponse, "description": "Permission denied"},
        404: {"model": ErrorResponse, "description": "No IoB data available"},
    },
)
async def get_iob_projection_endpoint(
    current_user: DiabeticOrAdminUser,
    db: AsyncSession = Depends(get_db),
) -> IoBProjectionResponse:
    """Get projected insulin-on-board (IoB) values (Story 3.7).

    This endpoint provides:
    - Last confirmed IoB from the pump
    - Current projected IoB based on insulin decay curve
    - Projected IoB values for 30 and 60 minutes ahead
    - Staleness warning if data is over 2 hours old

    Uses the user's configured DIA (defaults to 4 hours for Humalog/Novolog).

    Returns:
        IoBProjectionResponse with confirmed and projected IoB values
    """
    dia = await get_user_dia(db, current_user.id)
    projection = await get_iob_projection(db, current_user.id, dia_hours=dia)

    if projection is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="No IoB data available. Please sync your pump data first.",
        )

    return IoBProjectionResponse(
        confirmed_iob=projection.confirmed_iob,
        confirmed_at=projection.confirmed_at,
        projected_iob=projection.projected_iob,
        projected_at=projection.projected_at,
        projected_30min=projection.projected_30min,
        projected_60min=projection.projected_60min,
        minutes_since_confirmed=projection.minutes_since_confirmed,
        is_stale=projection.is_stale,
        stale_warning=projection.stale_warning,
        is_estimated=projection.is_estimated,
    )
