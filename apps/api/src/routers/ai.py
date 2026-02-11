"""Story 5.1 / 14.2: AI provider configuration router.

API endpoints for configuring AI provider with API key management.
Supports 5 provider types: claude_api, openai_api, claude_subscription,
chatgpt_subscription, and openai_compatible.
"""

import asyncio
from datetime import UTC, datetime

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from src.core.auth import DiabeticOrAdminUser
from src.core.encryption import decrypt_credential, encrypt_credential
from src.database import get_db
from src.logging_config import get_logger
from src.models.ai_provider import AIProviderConfig, AIProviderStatus
from src.schemas.ai_provider import (
    AIChatRequest,
    AIChatResponse,
    AIProviderConfigRequest,
    AIProviderConfigResponse,
    AIProviderDeleteResponse,
    AIProviderTestResponse,
)
from src.schemas.auth import ErrorResponse
from src.services.ai_provider import mask_api_key, validate_ai_api_key
from src.services.telegram_chat import handle_chat_web

logger = get_logger(__name__)

router = APIRouter(prefix="/api/ai", tags=["ai"])


def _build_response(
    config: AIProviderConfig, masked_key: str
) -> AIProviderConfigResponse:
    """Build an API response from an AIProviderConfig model instance.

    Args:
        config: The database model instance.
        masked_key: Pre-masked API key string for safe display.

    Returns:
        Populated response schema.
    """
    return AIProviderConfigResponse(
        provider_type=config.provider_type,
        status=config.status,
        model_name=config.model_name,
        base_url=config.base_url,
        masked_api_key=masked_key,
        last_validated_at=config.last_validated_at,
        last_error=config.last_error,
        created_at=config.created_at,
        updated_at=config.updated_at,
    )


@router.get(
    "/provider",
    response_model=AIProviderConfigResponse,
    responses={
        200: {"description": "Current AI provider configuration"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
        403: {"model": ErrorResponse, "description": "Permission denied"},
        404: {"model": ErrorResponse, "description": "No AI provider configured"},
    },
)
async def get_ai_provider(
    current_user: DiabeticOrAdminUser,
    db: AsyncSession = Depends(get_db),
) -> AIProviderConfigResponse:
    """Get the current AI provider configuration.

    Returns provider type, status, and masked API key.
    """
    result = await db.execute(
        select(AIProviderConfig).where(AIProviderConfig.user_id == current_user.id)
    )
    config = result.scalar_one_or_none()

    if not config:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="No AI provider configured",
        )

    decrypted_key = decrypt_credential(config.encrypted_api_key)
    masked = mask_api_key(decrypted_key)

    return _build_response(config, masked)


@router.post(
    "/provider",
    response_model=AIProviderConfigResponse,
    status_code=status.HTTP_201_CREATED,
    responses={
        201: {"description": "AI provider configured successfully"},
        400: {"model": ErrorResponse, "description": "Invalid API key"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
        403: {"model": ErrorResponse, "description": "Permission denied"},
    },
)
async def configure_ai_provider(
    request: AIProviderConfigRequest,
    current_user: DiabeticOrAdminUser,
    db: AsyncSession = Depends(get_db),
) -> AIProviderConfigResponse:
    """Configure AI provider with API key.

    Validates the API key, encrypts it, and stores the configuration.
    If a provider is already configured, it is updated.
    """
    # Validate the API key (run in thread to avoid blocking event loop)
    is_valid, error_message = await asyncio.to_thread(
        validate_ai_api_key,
        request.provider_type,
        request.api_key,
        base_url=request.base_url,
        model_name=request.model_name,
    )

    if not is_valid:
        logger.warning(
            "AI provider configuration failed",
            user_id=str(current_user.id),
            provider=request.provider_type.value,
            error=error_message,
        )
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=error_message,
        )

    # Check if configuration already exists
    result = await db.execute(
        select(AIProviderConfig).where(AIProviderConfig.user_id == current_user.id)
    )
    existing = result.scalar_one_or_none()

    now = datetime.now(UTC)

    if existing:
        # Update existing configuration
        existing.provider_type = request.provider_type
        existing.encrypted_api_key = encrypt_credential(request.api_key)
        existing.model_name = request.model_name
        existing.base_url = request.base_url
        existing.status = AIProviderStatus.CONNECTED
        existing.last_validated_at = now
        existing.last_error = None
        existing.updated_at = now
        config = existing
    else:
        # Create new configuration
        config = AIProviderConfig(
            user_id=current_user.id,
            provider_type=request.provider_type,
            encrypted_api_key=encrypt_credential(request.api_key),
            model_name=request.model_name,
            base_url=request.base_url,
            status=AIProviderStatus.CONNECTED,
            last_validated_at=now,
        )
        db.add(config)

    await db.commit()
    await db.refresh(config)

    logger.info(
        "AI provider configured successfully",
        user_id=str(current_user.id),
        provider=request.provider_type.value,
    )

    masked = mask_api_key(request.api_key)
    return _build_response(config, masked)


@router.delete(
    "/provider",
    response_model=AIProviderDeleteResponse,
    responses={
        200: {"description": "AI provider configuration removed"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
        403: {"model": ErrorResponse, "description": "Permission denied"},
        404: {"model": ErrorResponse, "description": "No AI provider configured"},
    },
)
async def delete_ai_provider(
    current_user: DiabeticOrAdminUser,
    db: AsyncSession = Depends(get_db),
) -> AIProviderDeleteResponse:
    """Remove AI provider configuration.

    Deletes the stored API key and provider settings.
    """
    result = await db.execute(
        select(AIProviderConfig).where(AIProviderConfig.user_id == current_user.id)
    )
    config = result.scalar_one_or_none()

    if not config:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="No AI provider configured",
        )

    await db.delete(config)
    await db.commit()

    logger.info(
        "AI provider configuration removed",
        user_id=str(current_user.id),
    )

    return AIProviderDeleteResponse(
        message="AI provider configuration removed successfully"
    )


@router.post(
    "/provider/test",
    response_model=AIProviderTestResponse,
    responses={
        200: {"description": "API key test result"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
        403: {"model": ErrorResponse, "description": "Permission denied"},
        404: {"model": ErrorResponse, "description": "No AI provider configured"},
    },
)
async def test_ai_provider(
    current_user: DiabeticOrAdminUser,
    db: AsyncSession = Depends(get_db),
) -> AIProviderTestResponse:
    """Test the stored AI provider API key.

    Decrypts the stored key and makes a validation request to the provider.
    """
    result = await db.execute(
        select(AIProviderConfig).where(AIProviderConfig.user_id == current_user.id)
    )
    config = result.scalar_one_or_none()

    if not config:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="No AI provider configured",
        )

    # Decrypt and test the key (run in thread to avoid blocking event loop)
    decrypted_key = decrypt_credential(config.encrypted_api_key)
    is_valid, error_message = await asyncio.to_thread(
        validate_ai_api_key,
        config.provider_type,
        decrypted_key,
        base_url=config.base_url,
        model_name=config.model_name,
    )

    now = datetime.now(UTC)

    if is_valid:
        config.status = AIProviderStatus.CONNECTED
        config.last_validated_at = now
        config.last_error = None
        config.updated_at = now
        await db.commit()

        return AIProviderTestResponse(
            success=True,
            message="AI provider configured successfully",
        )
    else:
        config.status = AIProviderStatus.ERROR
        config.last_error = error_message
        config.updated_at = now
        await db.commit()

        return AIProviderTestResponse(
            success=False,
            message=error_message or "API key validation failed",
        )


@router.post(
    "/chat",
    response_model=AIChatResponse,
    responses={
        200: {"description": "AI chat response"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
        403: {"model": ErrorResponse, "description": "Permission denied"},
        404: {"model": ErrorResponse, "description": "No AI provider configured"},
        502: {"model": ErrorResponse, "description": "AI provider error"},
    },
)
async def ai_chat(
    request: AIChatRequest,
    current_user: DiabeticOrAdminUser,
    db: AsyncSession = Depends(get_db),
) -> AIChatResponse:
    """Send a message to the AI with glucose context.

    Uses the user's configured AI provider and recent glucose data
    to generate a contextual response.
    """
    response_text = await handle_chat_web(db, current_user.id, request.message)

    return AIChatResponse(
        response=response_text,
        disclaimer="Not medical advice. Consult your healthcare provider.",
    )
