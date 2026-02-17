package com.glycemicgpt.mobile.data.auth

/**
 * Represents the authentication state of the user's session.
 * Observable via StateFlow from [AuthManager].
 */
sealed class AuthState {
    /** User has a valid access token. */
    data object Authenticated : AuthState()

    /** Token refresh is in progress. */
    data object Refreshing : AuthState()

    /** Session expired (refresh token invalid or expired). User must re-login. */
    data class Expired(val message: String = "Session expired, please sign in again") : AuthState()

    /** User is not logged in (no tokens stored). */
    data object Unauthenticated : AuthState()
}
