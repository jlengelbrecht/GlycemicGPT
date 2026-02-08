"""Story 5.6: Safety validation router.

API endpoint for querying safety validation audit logs.
"""

from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession

from src.core.auth import DiabeticOrAdminUser
from src.database import get_db
from src.schemas.auth import ErrorResponse
from src.schemas.safety_validation import SafetyLogListResponse, SafetyLogResponse
from src.services.safety_validation import list_safety_logs

router = APIRouter(prefix="/api/ai/safety", tags=["ai-safety"])


@router.get(
    "/logs",
    response_model=SafetyLogListResponse,
    responses={
        200: {"description": "List of safety validation logs"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
        403: {"model": ErrorResponse, "description": "Permission denied"},
    },
)
async def get_safety_logs(
    current_user: DiabeticOrAdminUser,
    db: AsyncSession = Depends(get_db),
    limit: int = Query(default=10, ge=1, le=50),
    offset: int = Query(default=0, ge=0),
) -> SafetyLogListResponse:
    """List safety validation logs for the current user."""
    logs, total = await list_safety_logs(
        current_user.id, db, limit=limit, offset=offset
    )
    return SafetyLogListResponse(
        logs=[SafetyLogResponse.model_validate(log) for log in logs],
        total=total,
    )
