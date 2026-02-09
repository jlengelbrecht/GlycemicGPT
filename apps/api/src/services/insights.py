"""Story 5.7: AI insights service.

Aggregates AI analyses into a unified insights feed and tracks
user responses (acknowledge/dismiss).
"""

import uuid

from fastapi import HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from src.logging_config import get_logger
from src.models.correction_analysis import CorrectionAnalysis
from src.models.daily_brief import DailyBrief
from src.models.meal_analysis import MealAnalysis
from src.models.suggestion_response import SuggestionResponse
from src.schemas.suggestion_response import InsightSummary

logger = get_logger(__name__)

# Map analysis type to its model class
ANALYSIS_MODELS: dict[str, type] = {
    "daily_brief": DailyBrief,
    "meal_analysis": MealAnalysis,
    "correction_analysis": CorrectionAnalysis,
}


def _brief_title(brief: DailyBrief) -> str:
    """Generate a title for a daily brief."""
    return f"Daily Brief — {brief.period_end.strftime('%b %d, %Y')}"


def _meal_title(analysis: MealAnalysis) -> str:
    """Generate a title for a meal analysis."""
    return f"Meal Pattern Analysis — {analysis.total_spikes} spike{'s' if analysis.total_spikes != 1 else ''} detected"


def _correction_title(analysis: CorrectionAnalysis) -> str:
    """Generate a title for a correction analysis."""
    return f"Correction Factor Analysis — {analysis.total_corrections} correction{'s' if analysis.total_corrections != 1 else ''} analyzed"


async def list_insights(
    user_id: uuid.UUID,
    db: AsyncSession,
    limit: int = 10,
) -> tuple[list[InsightSummary], int]:
    """List recent AI insights aggregated from all analysis types.

    Combines daily briefs, meal analyses, and correction analyses
    into a unified feed sorted by creation date.

    Args:
        user_id: User's UUID.
        db: Database session.
        limit: Maximum insights to return.

    Returns:
        Tuple of (insights list, total count).
    """
    insights: list[InsightSummary] = []

    # Fetch recent daily briefs
    briefs_result = await db.execute(
        select(DailyBrief)
        .where(DailyBrief.user_id == user_id)
        .order_by(DailyBrief.created_at.desc())
        .limit(limit)
    )
    briefs = list(briefs_result.scalars().all())

    # Fetch recent meal analyses
    meals_result = await db.execute(
        select(MealAnalysis)
        .where(MealAnalysis.user_id == user_id)
        .order_by(MealAnalysis.created_at.desc())
        .limit(limit)
    )
    meals = list(meals_result.scalars().all())

    # Fetch recent correction analyses
    corrections_result = await db.execute(
        select(CorrectionAnalysis)
        .where(CorrectionAnalysis.user_id == user_id)
        .order_by(CorrectionAnalysis.created_at.desc())
        .limit(limit)
    )
    corrections = list(corrections_result.scalars().all())

    # Collect all fetched IDs for scoped response lookup
    all_ids = (
        [brief.id for brief in briefs]
        + [meal.id for meal in meals]
        + [c.id for c in corrections]
    )

    # Fetch responses only for the fetched analyses (F6: scoped query)
    response_map: dict[tuple[str, uuid.UUID], str] = {}
    if all_ids:
        responses_result = await db.execute(
            select(
                SuggestionResponse.analysis_type,
                SuggestionResponse.analysis_id,
                SuggestionResponse.response,
            ).where(
                SuggestionResponse.user_id == user_id,
                SuggestionResponse.analysis_id.in_(all_ids),
            )
        )
        for row in responses_result.all():
            response_map[(row[0], row[1])] = row[2]

    # Build unified insights
    for brief in briefs:
        insight_status = response_map.get(("daily_brief", brief.id), "pending")
        insights.append(
            InsightSummary(
                id=brief.id,
                analysis_type="daily_brief",
                title=_brief_title(brief),
                content=brief.ai_summary,
                created_at=brief.created_at,
                status=insight_status,
            )
        )

    for meal in meals:
        insight_status = response_map.get(("meal_analysis", meal.id), "pending")
        insights.append(
            InsightSummary(
                id=meal.id,
                analysis_type="meal_analysis",
                title=_meal_title(meal),
                content=meal.ai_analysis,
                created_at=meal.created_at,
                status=insight_status,
            )
        )

    for correction in corrections:
        insight_status = response_map.get(
            ("correction_analysis", correction.id), "pending"
        )
        insights.append(
            InsightSummary(
                id=correction.id,
                analysis_type="correction_analysis",
                title=_correction_title(correction),
                content=correction.ai_analysis,
                created_at=correction.created_at,
                status=insight_status,
            )
        )

    # Sort by created_at descending and limit
    insights.sort(key=lambda x: x.created_at, reverse=True)
    total = len(insights)
    insights = insights[:limit]

    return insights, total


async def verify_analysis_ownership(
    user_id: uuid.UUID,
    analysis_type: str,
    analysis_id: uuid.UUID,
    db: AsyncSession,
) -> None:
    """Verify that an analysis exists and belongs to the user.

    Args:
        user_id: User's UUID.
        analysis_type: Type of analysis.
        analysis_id: ID of the analysis.
        db: Database session.

    Raises:
        HTTPException: 404 if not found or not owned by user.
    """
    model = ANALYSIS_MODELS.get(analysis_type)
    if model is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Invalid analysis type: {analysis_type}",
        )

    result = await db.execute(
        select(model.id).where(
            model.id == analysis_id,
            model.user_id == user_id,
        )
    )
    if result.scalar_one_or_none() is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Analysis not found",
        )


async def record_suggestion_response(
    user_id: uuid.UUID,
    analysis_type: str,
    analysis_id: uuid.UUID,
    response: str,
    reason: str | None,
    db: AsyncSession,
) -> SuggestionResponse:
    """Record a user's response to an AI suggestion.

    If the user has already responded to this analysis, returns 409 Conflict.

    Args:
        user_id: User's UUID.
        analysis_type: Type of analysis (daily_brief, meal_analysis, correction_analysis).
        analysis_id: ID of the analysis.
        response: User's response (acknowledged or dismissed).
        reason: Optional reason for the response.
        db: Database session.

    Returns:
        The created SuggestionResponse record.

    Raises:
        HTTPException: 409 if a response already exists.
    """
    # Check for existing response (F3: prevent duplicates)
    existing = await get_response_for_analysis(user_id, analysis_type, analysis_id, db)
    if existing is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="A response has already been recorded for this analysis",
        )

    entry = SuggestionResponse(
        user_id=user_id,
        analysis_type=analysis_type,
        analysis_id=analysis_id,
        response=response,
        reason=reason,
    )

    db.add(entry)
    await db.commit()
    await db.refresh(entry)

    logger.info(
        "Suggestion response recorded",
        user_id=str(user_id),
        analysis_type=analysis_type,
        analysis_id=str(analysis_id),
        response=response,
    )

    return entry


async def get_response_for_analysis(
    user_id: uuid.UUID,
    analysis_type: str,
    analysis_id: uuid.UUID,
    db: AsyncSession,
) -> SuggestionResponse | None:
    """Get the user's response for a specific analysis.

    Args:
        user_id: User's UUID.
        analysis_type: Type of analysis.
        analysis_id: ID of the analysis.
        db: Database session.

    Returns:
        The SuggestionResponse if found, None otherwise.
    """
    result = await db.execute(
        select(SuggestionResponse).where(
            SuggestionResponse.user_id == user_id,
            SuggestionResponse.analysis_type == analysis_type,
            SuggestionResponse.analysis_id == analysis_id,
        )
    )
    return result.scalar_one_or_none()
