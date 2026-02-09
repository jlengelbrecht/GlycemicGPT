"""Story 7.1: Telegram bot setup & configuration router.

Endpoints for linking/unlinking a Telegram account and sending test messages.
"""

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession

from src.config import settings
from src.core.auth import get_current_user
from src.database import get_db
from src.models.user import User
from src.schemas.telegram import (
    TelegramLinkResponse,
    TelegramStatusResponse,
    TelegramTestMessageResponse,
    TelegramUnlinkResponse,
    TelegramVerificationCodeResponse,
)
from src.services.telegram_bot import (
    TelegramBotError,
    generate_verification_code,
    get_bot_info,
    get_telegram_link,
    send_message,
    unlink_telegram,
)

router = APIRouter(
    prefix="/api/telegram",
    tags=["telegram"],
)


def _check_bot_configured() -> None:
    """Raise 503 if the Telegram bot token is not configured."""
    if not settings.telegram_bot_token:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Telegram bot is not configured",
        )


@router.get(
    "/status",
    response_model=TelegramStatusResponse,
)
async def get_telegram_status(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> TelegramStatusResponse:
    """Get the current Telegram link status.

    Returns whether the user has linked their Telegram account
    and the bot's username for linking instructions.
    """
    _check_bot_configured()

    try:
        bot_username = await get_bot_info()
    except TelegramBotError:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Telegram bot is temporarily unavailable",
        )

    link = await get_telegram_link(db, user.id)

    return TelegramStatusResponse(
        linked=link is not None,
        link=(
            TelegramLinkResponse(
                id=link.id,
                chat_id=link.chat_id,
                username=link.username,
                is_verified=link.is_verified,
                linked_at=link.linked_at,
            )
            if link
            else None
        ),
        bot_username=bot_username,
    )


@router.post(
    "/link",
    response_model=TelegramVerificationCodeResponse,
    status_code=status.HTTP_201_CREATED,
)
async def start_telegram_link(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> TelegramVerificationCodeResponse:
    """Generate a verification code for Telegram account linking.

    The user should send /start <code> to the bot on Telegram
    to complete the linking process.
    """
    _check_bot_configured()

    # Check if already linked
    existing = await get_telegram_link(db, user.id)
    if existing is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Telegram account is already linked",
        )

    try:
        bot_username = await get_bot_info()
    except TelegramBotError:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Telegram bot is temporarily unavailable",
        )

    code, expires_at = await generate_verification_code(db, user.id)

    return TelegramVerificationCodeResponse(
        code=code,
        expires_at=expires_at,
        bot_username=bot_username,
    )


@router.delete(
    "/link",
    response_model=TelegramUnlinkResponse,
)
async def remove_telegram_link(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> TelegramUnlinkResponse:
    """Unlink the user's Telegram account."""
    unlinked = await unlink_telegram(db, user.id)

    if not unlinked:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="No Telegram account is linked",
        )

    return TelegramUnlinkResponse(
        success=True,
        message="Telegram account has been unlinked",
    )


@router.post(
    "/test",
    response_model=TelegramTestMessageResponse,
)
async def send_test_message(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> TelegramTestMessageResponse:
    """Send a test message to the user's linked Telegram account."""
    _check_bot_configured()

    link = await get_telegram_link(db, user.id)
    if link is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="No Telegram account is linked",
        )

    try:
        await send_message(
            link.chat_id,
            "This is a test message from GlycemicGPT. "
            "Your Telegram notifications are working correctly!",
        )
    except TelegramBotError:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Failed to send test message. Please try again later.",
        )

    return TelegramTestMessageResponse(
        success=True,
        message="Test message sent successfully",
    )
