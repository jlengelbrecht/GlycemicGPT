"""Story 5.5: Correction factor analysis router.

API endpoints for correction bolus outcome analysis and ISF adjustment suggestions.
"""

import uuid

from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession

from src.core.auth import DiabeticOrAdminUser
from src.database import get_db
from src.schemas.auth import ErrorResponse
from src.schemas.correction_analysis import (
    AnalyzeCorrectionsRequest,
    CorrectionAnalysisListResponse,
    CorrectionAnalysisResponse,
)
from src.services.correction_analysis import (
    generate_correction_analysis,
    get_correction_analysis_by_id,
    list_correction_analyses,
)

router = APIRouter(prefix="/api/ai/corrections", tags=["ai-corrections"])


@router.post(
    "/analyze",
    response_model=CorrectionAnalysisResponse,
    status_code=201,
    responses={
        201: {"description": "Correction factor analysis generated"},
        400: {
            "model": ErrorResponse,
            "description": "Insufficient correction data",
        },
        401: {"model": ErrorResponse, "description": "Not authenticated"},
        403: {"model": ErrorResponse, "description": "Permission denied"},
        404: {
            "model": ErrorResponse,
            "description": "No AI provider configured",
        },
    },
)
async def analyze_corrections(
    request: AnalyzeCorrectionsRequest,
    current_user: DiabeticOrAdminUser,
    db: AsyncSession = Depends(get_db),
) -> CorrectionAnalysisResponse:
    """Analyze correction bolus outcomes and generate ISF suggestions.

    Examines manual correction boluses and subsequent glucose readings
    to identify under- and over-correction patterns by time of day.
    """
    analysis = await generate_correction_analysis(current_user, db, days=request.days)
    return CorrectionAnalysisResponse.model_validate(analysis)


@router.get(
    "",
    response_model=CorrectionAnalysisListResponse,
    responses={
        200: {"description": "List of correction analyses"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
        403: {"model": ErrorResponse, "description": "Permission denied"},
    },
)
async def get_correction_analyses(
    current_user: DiabeticOrAdminUser,
    db: AsyncSession = Depends(get_db),
    limit: int = Query(default=10, ge=1, le=50),
    offset: int = Query(default=0, ge=0),
) -> CorrectionAnalysisListResponse:
    """List correction factor analyses for the current user."""
    analyses, total = await list_correction_analyses(
        current_user.id, db, limit=limit, offset=offset
    )
    return CorrectionAnalysisListResponse(
        analyses=[CorrectionAnalysisResponse.model_validate(a) for a in analyses],
        total=total,
    )


@router.get(
    "/{analysis_id}",
    response_model=CorrectionAnalysisResponse,
    responses={
        200: {"description": "Correction analysis details"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
        403: {"model": ErrorResponse, "description": "Permission denied"},
        404: {"model": ErrorResponse, "description": "Analysis not found"},
    },
)
async def get_correction_analysis(
    analysis_id: uuid.UUID,
    current_user: DiabeticOrAdminUser,
    db: AsyncSession = Depends(get_db),
) -> CorrectionAnalysisResponse:
    """Get a specific correction factor analysis by ID."""
    analysis = await get_correction_analysis_by_id(analysis_id, current_user.id, db)
    return CorrectionAnalysisResponse.model_validate(analysis)
