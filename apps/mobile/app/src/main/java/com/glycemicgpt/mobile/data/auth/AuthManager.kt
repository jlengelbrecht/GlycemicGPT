package com.glycemicgpt.mobile.data.auth

import com.glycemicgpt.mobile.data.local.AuthTokenStore
import com.glycemicgpt.mobile.data.remote.dto.LoginResponse
import com.glycemicgpt.mobile.data.remote.dto.RefreshTokenRequest
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages authentication state and proactive token refresh.
 *
 * Responsibilities:
 * - Exposes observable [AuthState] via [authState]
 * - Validates tokens on startup
 * - Proactively refreshes access tokens before expiry
 * - Handles refresh failures gracefully (emits [AuthState.Expired])
 *
 * Thread safety: [performRefresh] is protected by a [Mutex] to prevent
 * concurrent refresh attempts (e.g., proactive timer + interceptor 401).
 */
@Singleton
class AuthManager @Inject constructor(
    private val authTokenStore: AuthTokenStore,
    private val refreshClientProvider: RefreshClientProvider,
    private val moshi: Moshi,
) {
    /** Dispatcher for blocking IO operations. Overridable for testing. */
    var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var refreshJob: Job? = null
    private val refreshMutex = Mutex()

    /**
     * Validates stored tokens on startup and schedules proactive refresh.
     * Call this from Application.onCreate() or the first ViewModel that loads.
     */
    fun validateOnStartup(scope: CoroutineScope) {
        val refreshToken = authTokenStore.getRefreshToken()
        if (refreshToken == null) {
            _authState.value = AuthState.Unauthenticated
            return
        }

        // Check if refresh token is expired
        if (authTokenStore.isRefreshTokenExpired()) {
            Timber.w("Refresh token expired on startup")
            _authState.value = AuthState.Expired()
            return
        }

        val accessToken = authTokenStore.getToken()
        if (accessToken != null) {
            // Valid access token exists
            _authState.value = AuthState.Authenticated
            scheduleProactiveRefresh(scope)
            return
        }

        // Access token expired but refresh token is valid -- attempt refresh
        scope.launch {
            performRefresh(scope)
        }
    }

    /**
     * Schedules a coroutine that refreshes the access token before it expires.
     * Re-schedules itself after each successful refresh.
     */
    fun scheduleProactiveRefresh(scope: CoroutineScope) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val expiresAt = authTokenStore.getTokenExpiresAtMs()
            if (expiresAt <= 0) return@launch

            val refreshAt = expiresAt - AuthTokenStore.PROACTIVE_REFRESH_WINDOW_MS
            val delayMs = (refreshAt - System.currentTimeMillis()).coerceAtLeast(0)

            Timber.d("Proactive refresh scheduled in ${delayMs / 1000}s")
            delay(delayMs)

            performRefresh(scope)
        }
    }

    /**
     * Attempts to refresh the access token using the stored refresh token.
     * Updates [authState] based on the result.
     *
     * Protected by [refreshMutex] to prevent concurrent refresh attempts.
     * Runs the blocking OkHttp call on [Dispatchers.IO].
     */
    suspend fun performRefresh(scope: CoroutineScope) {
        refreshMutex.withLock {
            // Re-check after acquiring the lock -- another coroutine may have refreshed already
            val existingToken = authTokenStore.getToken()
            if (existingToken != null && _authState.value is AuthState.Authenticated) {
                return
            }

            val refreshToken = authTokenStore.getRefreshToken()
            if (refreshToken == null) {
                _authState.value = AuthState.Unauthenticated
                return
            }

            if (authTokenStore.isRefreshTokenExpired()) {
                Timber.w("Refresh token expired, cannot refresh")
                _authState.value = AuthState.Expired()
                return
            }

            val baseUrl = authTokenStore.getBaseUrl()
            if (baseUrl.isNullOrBlank()) {
                Timber.w("No base URL configured, cannot refresh")
                _authState.value = AuthState.Expired()
                return
            }

            _authState.value = AuthState.Refreshing

            try {
                val body = RefreshTokenRequest(refreshToken = refreshToken)
                val adapter = moshi.adapter(RefreshTokenRequest::class.java)
                val json = adapter.toJson(body)

                val request = Request.Builder()
                    .url("http://localhost/api/auth/mobile/refresh")
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = withContext(ioDispatcher) {
                    refreshClientProvider.refreshClient.newCall(request).execute()
                }

                response.use { resp ->
                    if (resp.isSuccessful) {
                        val responseBody = resp.body?.string()
                        if (responseBody == null) {
                            Timber.w("Refresh response body is null")
                            _authState.value = AuthState.Expired()
                            return
                        }

                        val loginAdapter = moshi.adapter(LoginResponse::class.java)
                        val loginResponse = loginAdapter.fromJson(responseBody)
                        if (loginResponse == null) {
                            Timber.w("Failed to parse refresh response")
                            _authState.value = AuthState.Expired()
                            return
                        }

                        val expiresAtMs = System.currentTimeMillis() + (loginResponse.expiresIn * 1000L)
                        authTokenStore.saveCredentials(
                            baseUrl,
                            loginResponse.accessToken,
                            expiresAtMs,
                            loginResponse.user.email,
                        )
                        authTokenStore.saveRefreshToken(loginResponse.refreshToken)

                        Timber.d("Proactive token refresh succeeded")
                        _authState.value = AuthState.Authenticated
                        scheduleProactiveRefresh(scope)
                    } else {
                        Timber.w("Proactive token refresh failed with HTTP ${resp.code}")
                        authTokenStore.clearToken()
                        _authState.value = AuthState.Expired()
                    }
                }
            } catch (e: CancellationException) {
                throw e // Never swallow coroutine cancellation
            } catch (e: Exception) {
                Timber.w(e, "Proactive token refresh failed")
                // Network error -- don't clear tokens, stay authenticated if we had a valid token
                val currentToken = authTokenStore.getRawToken()
                if (currentToken != null) {
                    _authState.value = AuthState.Authenticated
                    // Retry later
                    scheduleProactiveRefresh(scope)
                } else {
                    _authState.value = AuthState.Expired()
                }
            }
        }
    }

    /** Called after a successful login to set the authenticated state. */
    fun onLoginSuccess(scope: CoroutineScope) {
        _authState.value = AuthState.Authenticated
        scheduleProactiveRefresh(scope)
    }

    /** Called on logout to reset state. */
    fun onLogout() {
        refreshJob?.cancel()
        refreshJob = null
        _authState.value = AuthState.Unauthenticated
    }

    /** Called by TokenRefreshInterceptor when a refresh attempt fails. */
    fun onRefreshFailed() {
        refreshJob?.cancel()
        _authState.value = AuthState.Expired()
    }

    /** Called by TokenRefreshInterceptor after a successful interceptor-driven refresh. */
    fun onInterceptorRefreshSuccess(scope: CoroutineScope) {
        _authState.value = AuthState.Authenticated
        scheduleProactiveRefresh(scope)
    }
}
