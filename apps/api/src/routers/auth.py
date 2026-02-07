"""Story 2.1, 2.2 & 2.3: Authentication router.

API endpoints for user registration, login, logout, and authentication.
"""

from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, Request, Response, status
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from src.config import settings
from src.core.auth import CurrentUser
from src.core.security import create_access_token, hash_password, verify_password
from src.database import get_db
from src.logging_config import get_logger
from src.models.user import User, UserRole
from src.schemas.auth import (
    ErrorResponse,
    LoginRequest,
    LoginResponse,
    LogoutResponse,
    UserRegistrationRequest,
    UserRegistrationResponse,
    UserResponse,
)

logger = get_logger(__name__)

router = APIRouter(prefix="/api/auth", tags=["authentication"])


@router.post(
    "/register",
    response_model=UserRegistrationResponse,
    status_code=status.HTTP_201_CREATED,
    responses={
        201: {"description": "User registered successfully"},
        400: {"model": ErrorResponse, "description": "Invalid request"},
        409: {"model": ErrorResponse, "description": "Email already exists"},
    },
)
async def register_user(
    request: UserRegistrationRequest,
    db: AsyncSession = Depends(get_db),
) -> UserRegistrationResponse:
    """Register a new user account.

    Creates a new user with the provided email and password.
    Password is hashed using bcrypt before storage.
    New users are assigned the 'diabetic' role by default.

    Args:
        request: Registration request with email and password
        db: Database session

    Returns:
        UserRegistrationResponse with user details and success message

    Raises:
        HTTPException 409: If email already exists
        HTTPException 400: If password doesn't meet requirements
    """
    # Check if email already exists
    existing_user = await db.execute(
        select(User).where(User.email == request.email.lower())
    )
    if existing_user.scalar_one_or_none():
        logger.warning(
            "Registration attempt with existing email",
            email=request.email,
        )
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="An account with this email already exists",
        )

    # Create new user
    user = User(
        email=request.email.lower(),
        hashed_password=hash_password(request.password),
        role=UserRole.DIABETIC,
        is_active=True,
        email_verified=False,
        disclaimer_acknowledged=False,
    )

    try:
        db.add(user)
        await db.commit()
        await db.refresh(user)

        logger.info(
            "User registered successfully",
            user_id=str(user.id),
            email=user.email,
        )

        return UserRegistrationResponse(
            id=user.id,
            email=user.email,
            role=user.role,
            message="Registration successful",
            disclaimer_required=not user.disclaimer_acknowledged,
        )

    except IntegrityError:
        await db.rollback()
        logger.warning(
            "Registration failed - integrity error",
            email=request.email,
        )
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="An account with this email already exists",
        )


# ============================================================================
# Story 2.2: Login Endpoint
# ============================================================================


@router.post(
    "/login",
    response_model=LoginResponse,
    responses={
        200: {"description": "Login successful"},
        401: {"model": ErrorResponse, "description": "Invalid credentials"},
    },
)
async def login(
    request: LoginRequest,
    response: Response,
    http_request: Request,
    db: AsyncSession = Depends(get_db),
) -> LoginResponse:
    """Authenticate a user and create a session.

    Validates credentials and returns a JWT token in an httpOnly cookie.
    The token expires after the configured session duration (default 24 hours).

    Args:
        request: Login request with email and password
        response: FastAPI response object for setting cookies
        http_request: HTTP request for logging client info
        db: Database session

    Returns:
        LoginResponse with user details and session info

    Raises:
        HTTPException 401: If credentials are invalid
    """
    # Get client IP for logging
    client_ip = http_request.client.host if http_request.client else "unknown"

    # Find user by email (case-insensitive)
    result = await db.execute(
        select(User).where(User.email == request.email.lower())
    )
    user = result.scalar_one_or_none()

    # Check if user exists and password is correct
    if not user or not verify_password(request.password, user.hashed_password):
        logger.warning(
            "Failed login attempt",
            email=request.email,
            client_ip=client_ip,
            reason="invalid_credentials",
        )
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid email or password",
        )

    # Check if user is active
    if not user.is_active:
        logger.warning(
            "Failed login attempt",
            email=request.email,
            client_ip=client_ip,
            reason="account_disabled",
        )
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid email or password",
        )

    # Create JWT token
    token = create_access_token(
        user_id=user.id,
        email=user.email,
        role=user.role.value,
    )

    # Set httpOnly cookie with the token
    response.set_cookie(
        key=settings.jwt_cookie_name,
        value=token,
        httponly=True,
        secure=True,  # Requires HTTPS in production
        samesite="lax",
        max_age=settings.session_expire_hours * 3600,
        path="/",
    )

    # Update last login timestamp
    user.last_login_at = datetime.now(timezone.utc)
    await db.commit()

    logger.info(
        "User logged in successfully",
        user_id=str(user.id),
        email=user.email,
        client_ip=client_ip,
    )

    return LoginResponse(
        message="Login successful",
        user=UserResponse.model_validate(user),
        disclaimer_required=not user.disclaimer_acknowledged,
    )


@router.get(
    "/me",
    response_model=UserResponse,
    responses={
        200: {"description": "Current user details"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
    },
)
async def get_current_user_profile(
    current_user: CurrentUser,
) -> UserResponse:
    """Get the current authenticated user's profile.

    Requires a valid session cookie. Returns the user's profile information.

    Args:
        current_user: The authenticated user from the session cookie

    Returns:
        UserResponse with the current user's details
    """
    return UserResponse.model_validate(current_user)


# ============================================================================
# Story 2.3: Logout Endpoint
# ============================================================================


@router.post(
    "/logout",
    response_model=LogoutResponse,
    responses={
        200: {"description": "Logout successful"},
        401: {"model": ErrorResponse, "description": "Not authenticated"},
    },
)
async def logout(
    response: Response,
    current_user: CurrentUser,
) -> LogoutResponse:
    """Log out the current user and terminate their session.

    Clears the session cookie, effectively invalidating the session.
    Requires a valid session to log out.

    Args:
        response: FastAPI response object for clearing cookies
        current_user: The authenticated user (validates session is active)

    Returns:
        LogoutResponse with success message
    """
    # Clear the session cookie by setting it to expire immediately
    response.delete_cookie(
        key=settings.jwt_cookie_name,
        path="/",
        secure=True,
        httponly=True,
        samesite="lax",
    )

    logger.info(
        "User logged out successfully",
        user_id=str(current_user.id),
        email=current_user.email,
    )

    return LogoutResponse(message="Logout successful")
