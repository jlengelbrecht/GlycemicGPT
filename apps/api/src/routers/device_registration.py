"""Story 16.11: Device registration API.

Allows mobile apps to register/unregister for alert delivery.
"""

from fastapi import APIRouter, Depends, HTTPException, Request, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from src.core.auth import CurrentUser
from src.database import get_db
from src.logging_config import get_logger
from src.middleware.rate_limit import limiter
from src.services.device_service import register_device, unregister_device

logger = get_logger(__name__)

router = APIRouter(prefix="/api/v1/devices", tags=["devices"])


class DeviceRegistrationRequest(BaseModel):
    """Request to register a device."""

    device_token: str = Field(..., min_length=1, max_length=255)
    device_name: str = Field(..., min_length=1, max_length=255)
    platform: str = Field(default="android", max_length=50)


class DeviceRegistrationResponse(BaseModel):
    """Response after device registration."""

    id: str
    device_token: str


@router.post(
    "/register",
    response_model=DeviceRegistrationResponse,
    status_code=status.HTTP_200_OK,
)
@limiter.limit("10/minute")
async def register_device_endpoint(
    body: DeviceRegistrationRequest,
    request: Request,
    current_user: CurrentUser,
    db: AsyncSession = Depends(get_db),
) -> DeviceRegistrationResponse:
    """Register a mobile device for alert delivery."""
    device = await register_device(
        db=db,
        user_id=current_user.id,
        device_token=body.device_token,
        device_name=body.device_name,
        platform=body.platform,
    )
    return DeviceRegistrationResponse(
        id=str(device.id),
        device_token=device.device_token,
    )


@router.delete(
    "/{device_token}",
    status_code=status.HTTP_204_NO_CONTENT,
)
async def unregister_device_endpoint(
    device_token: str,
    current_user: CurrentUser,
    db: AsyncSession = Depends(get_db),
) -> None:
    """Unregister a device (e.g., on logout). Only the device owner can unregister."""
    removed = await unregister_device(
        db=db,
        device_token=device_token,
        user_id=current_user.id,
    )
    if not removed:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Device not found",
        )
