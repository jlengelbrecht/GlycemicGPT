"""Story 5.3: Daily brief router.

API endpoints for generating and retrieving AI-powered daily glucose briefs.
"""

import uuid

from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession

from src.core.auth import DiabeticOrAdminUser
from src.database import get_db
from src.schemas.auth import ErrorResponse
from src.schemas.daily_brief import (
    DailyBriefListResponse,
    DailyBriefResponse,
    GenerateBriefRequest,
)
from src.services.daily_brief import generate_daily_brief, get_brief_by_id, list_briefs

router = APIRouter(prefix="/api/ai/briefs", tags=["ai-briefs"])


@router.post(
    "/generate",
    response_model=DailyBriefResponse,
    status_code=201,
    responses={
        201: {"description": "Daily brief generated successfully"},
        400: {"model": ErrorResponse, "description": "Insufficient data"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
        403: {"model": ErrorResponse, "description": "Permission denied"},
        404: {"model": ErrorResponse, "description": "No AI provider configured"},
    },
)
async def generate_brief(
    request: GenerateBriefRequest,
    current_user: DiabeticOrAdminUser,
    db: AsyncSession = Depends(get_db),
) -> DailyBriefResponse:
    """Generate an AI-powered daily brief.

    Analyzes glucose readings and pump events for the specified period
    and generates an AI summary.
    """
    brief = await generate_daily_brief(current_user, db, hours=request.hours)
    return DailyBriefResponse.model_validate(brief)


@router.get(
    "",
    response_model=DailyBriefListResponse,
    responses={
        200: {"description": "List of daily briefs"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
        403: {"model": ErrorResponse, "description": "Permission denied"},
    },
)
async def get_briefs(
    current_user: DiabeticOrAdminUser,
    db: AsyncSession = Depends(get_db),
    limit: int = Query(default=10, ge=1, le=50),
    offset: int = Query(default=0, ge=0),
) -> DailyBriefListResponse:
    """List daily briefs for the current user.

    Returns briefs ordered by most recent first, with pagination.
    """
    briefs, total = await list_briefs(current_user.id, db, limit=limit, offset=offset)
    return DailyBriefListResponse(
        briefs=[DailyBriefResponse.model_validate(b) for b in briefs],
        total=total,
    )


@router.get(
    "/{brief_id}",
    response_model=DailyBriefResponse,
    responses={
        200: {"description": "Daily brief details"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
        403: {"model": ErrorResponse, "description": "Permission denied"},
        404: {"model": ErrorResponse, "description": "Brief not found"},
    },
)
async def get_brief(
    brief_id: uuid.UUID,
    current_user: DiabeticOrAdminUser,
    db: AsyncSession = Depends(get_db),
) -> DailyBriefResponse:
    """Get a specific daily brief by ID."""
    brief = await get_brief_by_id(brief_id, current_user.id, db)
    return DailyBriefResponse.model_validate(brief)
