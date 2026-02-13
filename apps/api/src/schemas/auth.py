"""Story 2.1, 2.2 & 2.3: Authentication schemas.

Pydantic schemas for user registration, login, logout, and authentication.
"""

import re
import uuid
from datetime import datetime

from pydantic import BaseModel, EmailStr, Field, field_validator

from src.models.user import UserRole


class UserRegistrationRequest(BaseModel):
    """Request schema for user registration."""

    email: EmailStr = Field(..., description="User's email address")
    password: str = Field(
        ...,
        min_length=8,
        max_length=128,
        description="Password (min 8 chars, must include uppercase, lowercase, and number)",
    )

    @field_validator("password")
    @classmethod
    def validate_password_strength(cls, v: str) -> str:
        """Validate password meets strength requirements."""
        if not re.search(r"[a-z]", v):
            raise ValueError(
                "Password must be at least 8 characters with uppercase, lowercase, and number"
            )
        if not re.search(r"[A-Z]", v):
            raise ValueError(
                "Password must be at least 8 characters with uppercase, lowercase, and number"
            )
        if not re.search(r"\d", v):
            raise ValueError(
                "Password must be at least 8 characters with uppercase, lowercase, and number"
            )
        return v


class UserRegistrationResponse(BaseModel):
    """Response schema for successful registration."""

    id: uuid.UUID = Field(..., description="User's unique identifier")
    email: str = Field(..., description="User's email address")
    role: UserRole = Field(..., description="Assigned user role")
    message: str = Field(default="Registration successful")
    disclaimer_required: bool = Field(
        default=True,
        description="Whether user needs to acknowledge disclaimer",
    )


class UserResponse(BaseModel):
    """Public user information response."""

    model_config = {"from_attributes": True}

    id: uuid.UUID
    email: str
    display_name: str | None = None
    role: UserRole
    is_active: bool
    email_verified: bool
    disclaimer_acknowledged: bool
    created_at: datetime


class ErrorResponse(BaseModel):
    """Error response schema."""

    detail: str = Field(..., description="Error message")


# ============================================================================
# Story 2.2: Login Schemas
# ============================================================================


class LoginRequest(BaseModel):
    """Request schema for user login."""

    email: EmailStr = Field(..., description="User's email address")
    password: str = Field(..., min_length=1, description="User's password")


class LoginResponse(BaseModel):
    """Response schema for successful login."""

    message: str = Field(default="Login successful")
    user: UserResponse = Field(..., description="Authenticated user details")
    disclaimer_required: bool = Field(
        default=False,
        description="Whether user needs to acknowledge disclaimer",
    )


class TokenResponse(BaseModel):
    """Response schema for token info (used when not using cookies)."""

    access_token: str = Field(..., description="JWT access token")
    token_type: str = Field(default="bearer")
    expires_in: int = Field(..., description="Token expiration in seconds")


class MobileLoginResponse(TokenResponse):
    """Response schema for mobile login (returns JWT in body instead of cookie)."""

    user: UserResponse = Field(..., description="Authenticated user details")


# ============================================================================
# Story 2.3: Logout Schemas
# ============================================================================


class LogoutResponse(BaseModel):
    """Response schema for successful logout."""

    message: str = Field(default="Logout successful")


# ============================================================================
# Story 10.2: Profile Update Schemas
# ============================================================================


class ProfileUpdateRequest(BaseModel):
    """Request schema for updating user profile."""

    display_name: str | None = Field(
        default=None,
        max_length=100,
        description="Display name (max 100 chars)",
    )

    @field_validator("display_name")
    @classmethod
    def strip_display_name(cls, v: str | None) -> str | None:
        """Strip whitespace and convert empty strings to None."""
        if v is not None:
            v = v.strip()
            if not v:
                return None
        return v


class PasswordChangeRequest(BaseModel):
    """Request schema for changing password."""

    current_password: str = Field(
        ..., min_length=1, description="Current password for verification"
    )
    new_password: str = Field(
        ...,
        min_length=8,
        max_length=128,
        description="New password (min 8 chars, must include uppercase, lowercase, and number)",
    )

    @field_validator("new_password")
    @classmethod
    def validate_password_strength(cls, v: str) -> str:
        """Validate new password meets strength requirements."""
        if not re.search(r"[a-z]", v):
            raise ValueError(
                "Password must be at least 8 characters with uppercase, lowercase, and number"
            )
        if not re.search(r"[A-Z]", v):
            raise ValueError(
                "Password must be at least 8 characters with uppercase, lowercase, and number"
            )
        if not re.search(r"\d", v):
            raise ValueError(
                "Password must be at least 8 characters with uppercase, lowercase, and number"
            )
        return v
