"""Story 1.5: Correlation ID Middleware.

Middleware that generates or extracts correlation IDs for request tracing.

Uses pure ASGI middleware pattern to avoid async event loop issues
that occur with Starlette's BaseHTTPMiddleware and asyncpg connections.
"""

import time
import uuid
from collections.abc import Awaitable, Callable
from typing import Any

from starlette.requests import Request
from starlette.responses import Response
from starlette.types import ASGIApp, Message, Receive, Scope, Send

from src.logging_config import correlation_id_ctx, get_logger

logger = get_logger(__name__)

# Header name for correlation ID
CORRELATION_ID_HEADER = "X-Correlation-ID"


class CorrelationIdMiddleware:
    """Pure ASGI middleware that adds correlation IDs to requests.

    If the incoming request has an X-Correlation-ID header, it uses that value.
    Otherwise, it generates a new UUID for the request.
    The correlation ID is:
    - Set in context for use throughout the request
    - Added to the response headers
    - Logged with all log messages

    This uses the pure ASGI pattern instead of BaseHTTPMiddleware to avoid
    async event loop issues with SQLAlchemy asyncpg connections.
    """

    def __init__(self, app: ASGIApp) -> None:
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        """Process ASGI request."""
        if scope["type"] not in ("http", "websocket"):
            await self.app(scope, receive, send)
            return

        # Extract or generate correlation ID
        headers = dict(scope.get("headers", []))
        correlation_id = headers.get(b"x-correlation-id", b"").decode() or str(
            uuid.uuid4()
        )

        # Set in context for logging
        token = correlation_id_ctx.set(correlation_id)

        # Track timing and response status
        start_time = time.perf_counter()
        status_code: int | None = None

        # Get request info for logging
        method = scope.get("method", "")
        path = scope.get("path", "")
        client = scope.get("client")
        client_ip = client[0] if client else None

        # Log request start
        logger.info(
            "Request started",
            method=method,
            path=path,
            client_ip=client_ip,
        )

        async def send_wrapper(message: Message) -> None:
            """Wrapper to capture response status and add correlation header."""
            nonlocal status_code

            if message["type"] == "http.response.start":
                status_code = message.get("status")
                # Add correlation ID to response headers
                headers = list(message.get("headers", []))
                headers.append(
                    (CORRELATION_ID_HEADER.lower().encode(), correlation_id.encode())
                )
                message = {**message, "headers": headers}

            await send(message)

        try:
            await self.app(scope, receive, send_wrapper)

            # Log request completion
            duration_ms = (time.perf_counter() - start_time) * 1000
            logger.info(
                "Request completed",
                method=method,
                path=path,
                status_code=status_code,
                duration_ms=round(duration_ms, 2),
            )
        except Exception:
            duration_ms = (time.perf_counter() - start_time) * 1000
            logger.exception(
                "Request failed",
                method=method,
                path=path,
                duration_ms=round(duration_ms, 2),
            )
            raise
        finally:
            # Reset context
            correlation_id_ctx.reset(token)
