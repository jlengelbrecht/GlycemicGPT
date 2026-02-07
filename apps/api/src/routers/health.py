"""Health check endpoints for container orchestration."""

from typing import Any

from fastapi import APIRouter, Response, status
from fastapi.responses import JSONResponse

from src.database import check_database_connection

router = APIRouter(tags=["Health"])


@router.get("/health", response_model=None)
async def health_check() -> Response:
    """
    Health check endpoint with database status.

    Returns:
        {"status": "healthy", "database": "connected"} when all systems operational
        {"status": "degraded", "database": "disconnected"} when database unavailable

    Used by Docker health checks and load balancers.
    """
    db_connected = await check_database_connection()

    if db_connected:
        return JSONResponse(
            status_code=status.HTTP_200_OK,
            content={
                "status": "healthy",
                "database": "connected",
            },
        )
    else:
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content={
                "status": "degraded",
                "database": "disconnected",
            },
        )


@router.get("/health/live")
async def liveness_probe() -> dict[str, Any]:
    """
    Kubernetes liveness probe.

    Returns success if the application process is running.
    This should NOT check external dependencies like databases.
    If this fails, Kubernetes will restart the container.
    """
    return {"status": "alive"}


@router.get("/health/ready", response_model=None)
async def readiness_probe() -> Response:
    """
    Kubernetes readiness probe.

    Returns success if the application is ready to serve traffic.
    Checks database connectivity to ensure the service can handle requests.
    If this fails, Kubernetes will stop routing traffic to this pod.
    """
    db_connected = await check_database_connection()

    if db_connected:
        return JSONResponse(
            status_code=status.HTTP_200_OK,
            content={
                "status": "ready",
                "database": "connected",
            },
        )
    else:
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content={
                "status": "not_ready",
                "database": "disconnected",
            },
        )
