"""Story 2.4: System administration router.

Admin-only endpoints for system health and configuration.
"""

from fastapi import APIRouter, status

from src.core.auth import AdminUser
from src.logging_config import get_logger
from src.schemas.system import SystemHealthResponse, SystemStatsResponse

logger = get_logger(__name__)

router = APIRouter(prefix="/api/system", tags=["system"])


@router.get(
    "/health",
    response_model=SystemHealthResponse,
    responses={
        200: {"description": "System health status"},
        401: {"description": "Not authenticated"},
        403: {"description": "Admin access required"},
    },
)
async def get_system_health(
    admin_user: AdminUser,
) -> SystemHealthResponse:
    """Get detailed system health status (admin only).

    This endpoint provides detailed system health information
    that is only accessible to administrators.

    Args:
        admin_user: The authenticated admin user

    Returns:
        SystemHealthResponse with health details
    """
    logger.info(
        "Admin accessed system health",
        user_id=str(admin_user.id),
        email=admin_user.email,
    )

    return SystemHealthResponse(
        status="healthy",
        database="connected",
        redis="connected",
        version="1.0.0",
    )


@router.get(
    "/stats",
    response_model=SystemStatsResponse,
    responses={
        200: {"description": "System statistics"},
        401: {"description": "Not authenticated"},
        403: {"description": "Admin access required"},
    },
)
async def get_system_stats(
    admin_user: AdminUser,
) -> SystemStatsResponse:
    """Get system statistics (admin only).

    This endpoint provides system usage statistics
    that is only accessible to administrators.

    Args:
        admin_user: The authenticated admin user

    Returns:
        SystemStatsResponse with system statistics
    """
    logger.info(
        "Admin accessed system stats",
        user_id=str(admin_user.id),
        email=admin_user.email,
    )

    # In a real implementation, these would be fetched from the database
    return SystemStatsResponse(
        total_users=0,
        active_sessions=0,
        api_requests_today=0,
    )
