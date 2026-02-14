"""Story 16.12: Rate limiting middleware using slowapi.

Protects auth and data push endpoints from abuse.
"""

from slowapi import Limiter
from slowapi.errors import RateLimitExceeded
from starlette.requests import Request
from starlette.responses import JSONResponse

from src.config import settings

# Use in-memory storage during tests (no Redis dependency), otherwise Redis
_storage_uri = (
    "memory://"
    if settings.testing
    else (settings.redis_url if settings.redis_url else "memory://")
)


def _get_real_client_ip(request: Request) -> str:
    """Extract real client IP, respecting X-Forwarded-For behind reverse proxies."""
    forwarded_for = request.headers.get("x-forwarded-for")
    if forwarded_for:
        # Take the first (leftmost) IP -- the original client
        return forwarded_for.split(",")[0].strip()
    real_ip = request.headers.get("x-real-ip")
    if real_ip:
        return real_ip.strip()
    return request.client.host if request.client else "unknown"


limiter = Limiter(
    key_func=_get_real_client_ip,
    storage_uri=_storage_uri,
    enabled=not settings.testing,
)

# Rate limits are applied per-endpoint via @limiter.limit() decorators:
# Auth login endpoints: 10/minute
# Refresh endpoint: 30/minute
# Device registration: 10/minute
# Pump push: 60/minute


async def rate_limit_exceeded_handler(
    request: Request, exc: RateLimitExceeded
) -> JSONResponse:
    """Return a 429 JSON response when rate limit is exceeded."""
    return JSONResponse(
        status_code=429,
        content={"detail": f"Rate limit exceeded: {exc.detail}"},
    )
