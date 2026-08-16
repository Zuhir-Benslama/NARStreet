package com.nars.maplibre.data.api

import com.nars.maplibre.AppPreferences
import com.nars.maplibre.BuildConfig
import com.nars.maplibre.data.model.LoginApiResponse
import com.nars.maplibre.data.model.LoginResponse
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.User
import com.nars.maplibre.utils.NarsLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Suppress("TooManyFunctions")
class ApiService(private val httpClient: HttpClient, private val preferences: AppPreferences) {
    companion object {
        private const val TAG = "ApiService"
        private val COOKIE_ACCESS_TOKEN_REGEX = Regex("access_token=([^;]+)")
        private val COOKIE_REFRESH_TOKEN_REGEX = Regex("refresh_token=([^;]+)")

        /** Max rows the backend returns per page (clamped server-side). */
        private const val FEATURES_PAGE_SIZE = 500
    }

    private val baseUrl: String = BuildConfig.API_BASE_URL.trimEnd('/')

    @Volatile private var sessionToken: String? = null

    @Volatile private var refreshToken: String? = null

    /**
     * Serializes token refresh so concurrent 401s trigger a single rotation.
     * The backend rotates refresh tokens on every refresh; without this lock two
     * in-flight requests could each refresh with the same (now stale) token,
     * silently invalidating the session.
     */
    private val refreshMutex = Mutex()

    /**
     * Emitted when the backend rejects the refresh token (401/403 on
     * /api/refresh), meaning the session is permanently invalid. UI layers
     * observe this to navigate back to login instead of leaving the user on a
     * dead session.
     */
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    fun setSessionToken(token: String?) {
        sessionToken = token
    }

    fun getSessionToken(): String? = sessionToken

    fun setRefreshToken(token: String?) {
        refreshToken = token
    }

    fun getRefreshToken(): String? = refreshToken

    /**
     * Extracts the access + refresh token cookies issued by the backend.
     * The backend sets both cookies on signin and on every /api/refresh.
     * Returns true when an access-token cookie was present, so callers can
     * distinguish "a fresh access token was issued" from "nothing changed".
     */
    private fun extractAndSetCookies(response: io.ktor.client.statement.HttpResponse): Boolean {
        var sawAccessToken = false
        response.headers.getAll(HttpHeaders.SetCookie)?.forEach { rawCookie ->
            COOKIE_ACCESS_TOKEN_REGEX.find(rawCookie)?.let { match ->
                sessionToken = match.groupValues[1]
                sawAccessToken = true
            }
            COOKIE_REFRESH_TOKEN_REGEX.find(rawCookie)?.let { match ->
                refreshToken = match.groupValues[1]
            }
        }
        return sawAccessToken
    }

    /**
     * Persists the current in-memory session tokens so the session survives
     * process death. Called from both login and token refresh, keeping the
     * transport layer as the single owner of token persistence.
     */
    private fun persistTokens() {
        preferences.authToken = sessionToken
        preferences.refreshToken = refreshToken
    }

    /**
     * Drops the session entirely — used when the backend rejects a refresh
     * (expired/revoked refresh token) or on logout.
     */
    private fun clearTokens() {
        sessionToken = null
        refreshToken = null
        persistTokens()
    }

    private fun buildUserFromResponse(apiResponse: LoginApiResponse): User {
        val commune = apiResponse.user.commune
        return commune?.toUserFields(apiResponse.user) ?: User(
            id = apiResponse.user.id,
            username = apiResponse.user.username,
            name = apiResponse.user.name,
            email = apiResponse.user.email,
            role = apiResponse.user.role,
        )
    }

    private fun authHeaders(): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        sessionToken?.let { token ->
            headers["Authorization"] = "Bearer $token"
        }
        return headers
    }

    /**
     * Executes an authenticated request, transparently refreshing the access
     * token once when the server responds 401 (access token expiry).
     */
    private suspend fun authenticatedRequest(block: suspend () -> HttpResponse): HttpResponse {
        val accessTokenBeforeRequest = sessionToken
        var response = block()
        if (response.status == HttpStatusCode.Unauthorized) {
            val refreshed =
                refreshMutex.withLock {
                    if (sessionToken != accessTokenBeforeRequest) {
                        // Another coroutine already rotated the access token that
                        // this request used — reuse it instead of refreshing again.
                        true
                    } else {
                        tryRefreshToken()
                    }
                }
            if (refreshed) {
                response = block()
            }
        }
        return response
    }

    /**
     * Attempts to rotate the refresh token via POST /api/refresh.
     * Returns true only when a fresh access token was actually issued.
     */
    private suspend fun tryRefreshToken(): Boolean {
        // Fall back to the persisted token: the app clears the in-memory tokens
        // while backgrounded (see NarsApplication), so a request that 401s at
        // that moment must refresh from the encrypted prefs instead of failing.
        val token = refreshToken ?: preferences.refreshToken ?: return false
        return try {
            val response =
                httpClient.post("$baseUrl/api/refresh") {
                    header(HttpHeaders.Cookie, "refresh_token=$token")
                }
            // A rejected refresh token (401/403) means the session is permanently
            // dead: clear it and let observers navigate to login. Any other
            // failure is a transient server error — keep the session intact so a
            // later user action can simply retry.
            if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                clearTokens()
                _sessionExpired.tryEmit(Unit)
                return false
            }
            if (!response.status.isSuccess()) {
                NarsLogger.w(TAG, "Token refresh failed (HTTP ${response.status.value}) — keeping session for retry")
                return false
            }
            val accessIssued = extractAndSetCookies(response)
            persistTokens()
            accessIssued
        } catch (e: CancellationException) {
            throw e
        } catch (e: java.io.IOException) {
            NarsLogger.w(TAG, "Token refresh failed", e)
            false
        }
    }

    /**
     * Authenticate with the NARS API.
     * Extracts session cookie and bearer token from the response on success.
     */
    suspend fun login(username: String, password: String): Result<LoginResponse> {
        return try {
            val response =
                httpClient.post("$baseUrl/api/signin") {
                    contentType(ContentType.Application.Json)
                    setBody(LoginRequest(username, password))
                }

            if (!response.status.isSuccess()) {
                val body = response.bodyAsText()
                val errorMessage =
                    parseErrorMessage(body) ?: "Login failed: HTTP ${response.status.value}"
                return Result.failure(Exception(errorMessage))
            }

            val body = response.bodyAsText()
            val apiResponse = apiJson.decodeFromString<LoginApiResponse>(body)

            if (!apiResponse.success) {
                return Result.failure(Exception(apiResponse.message ?: "Login failed"))
            }

            // Only adopt the session cookies after the response has been fully
            // validated — otherwise a failed login (bad body or success=false)
            // would still leave live tokens in memory while reporting failure.
            extractAndSetCookies(response)

            val token = apiResponse.token ?: apiResponse.accessToken
            token?.let { sessionToken = it }

            persistTokens()

            val user = buildUserFromResponse(apiResponse)
            NarsLogger.logAuthEvent(TAG, "Login successful", username)
            Result.success(LoginResponse(user, token, user.communeName))
        } catch (e: CancellationException) {
            throw e
        } catch (e: kotlinx.serialization.SerializationException) {
            NarsLogger.e(TAG, "Login failed", e)
            Result.failure(e)
        } catch (e: java.io.IOException) {
            NarsLogger.e(TAG, "Login failed", e)
            Result.failure(e)
        }
    }

    /**
     * Extracts a user-facing message from a failed-login body: the backend's
     * own payload first, then an RFC 7807 Problem Details payload, then null.
     */
    private fun parseErrorMessage(body: String): String? {
        try {
            apiJson.decodeFromString<LoginApiResponse>(body).message?.let { return it }
        } catch (_: kotlinx.serialization.SerializationException) {
            // Not a LoginApiResponse body.
        }
        try {
            val problem = apiJson.decodeFromString<ApiProblemDetails>(body)
            return problem.detail ?: problem.title
        } catch (_: kotlinx.serialization.SerializationException) {
            // Not a Problem Details body.
        }
        return null
    }

    suspend fun logout(): Result<Unit> = try {
        // Go through authenticatedRequest so a stale access token is refreshed
        // first — otherwise logout would 401 and the refresh token would never
        // be revoked on the server.
        val response =
            authenticatedRequest {
                httpClient.post("$baseUrl/api/logout") {
                    authHeaders().forEach { (k, v) -> headers.append(k, v) }
                    contentType(ContentType.Application.Json)
                }
            }
        if (!response.status.isSuccess()) {
            Result.failure(Exception("Logout failed: HTTP ${response.status.value}"))
        } else {
            Result.success(Unit)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: java.io.IOException) {
        NarsLogger.e(TAG, "Logout failed", e)
        Result.failure(e)
    }

    suspend fun loadFeatures(): Result<List<NarsFeature>> {
        return try {
            val allFeatures = mutableListOf<NarsFeature>()
            var skip = 0
            var hasMore = true
            while (hasMore) {
                val response =
                    authenticatedRequest {
                        httpClient.get("$baseUrl/api/features") {
                            authHeaders().forEach { (k, v) -> headers.append(k, v) }
                            parameter("skip", skip)
                            parameter("take", FEATURES_PAGE_SIZE)
                        }
                    }
                if (!response.status.isSuccess()) {
                    val error = Exception("Load failed: HTTP ${response.status.value}")
                    NarsLogger.e(TAG, "loadFeatures failed", error)
                    return Result.failure(error)
                }
                val body = response.bodyAsText()
                val apiResponse = body.ifBlank { null }?.let {
                    apiJson.decodeFromString<ApiLoadFeaturesResponse>(it)
                }
                if (apiResponse == null) {
                    hasMore = false
                } else {
                    val features = apiResponse.features
                    allFeatures += features.mapNotNull { it.toNarsFeature() }
                    skip += features.size
                    hasMore = features.isNotEmpty() && apiResponse.count > skip
                }
            }
            Result.success(allFeatures)
        } catch (e: CancellationException) {
            throw e
        } catch (e: kotlinx.serialization.SerializationException) {
            NarsLogger.e(TAG, "loadFeatures failed", e)
            Result.failure(e)
        } catch (e: java.io.IOException) {
            NarsLogger.e(TAG, "loadFeatures failed", e)
            Result.failure(e)
        }
    }

    suspend fun saveFeature(feature: NarsFeature): Result<String> = try {
        val requestBody = apiJson.encodeToString(feature.toApiSaveRequest())
        val response =
            authenticatedRequest {
                httpClient.post("$baseUrl/api/features") {
                    authHeaders().forEach { (k, v) -> headers.append(k, v) }
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
            }
        if (!response.status.isSuccess()) {
            val error = Exception("Save failed: HTTP ${response.status.value}")
            NarsLogger.e(TAG, "saveFeature failed", error)
            return Result.failure(error)
        }
        val id =
            apiJson.decodeFromString<ApiSaveFeatureResponse>(response.bodyAsText()).id
                ?: feature.id
        Result.success(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: kotlinx.serialization.SerializationException) {
        NarsLogger.e(TAG, "saveFeature failed", e)
        Result.failure(e)
    } catch (e: java.io.IOException) {
        NarsLogger.e(TAG, "saveFeature failed", e)
        Result.failure(e)
    }

    suspend fun updateFeature(featureId: String, feature: NarsFeature): Result<Unit> = try {
        val requestBody = apiJson.encodeToString(feature.toApiUpdateRequest())
        val response =
            authenticatedRequest {
                httpClient.put("$baseUrl/api/features/$featureId") {
                    authHeaders().forEach { (k, v) -> headers.append(k, v) }
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
            }
        if (!response.status.isSuccess()) {
            val error = Exception("Update failed: HTTP ${response.status.value}")
            NarsLogger.e(TAG, "updateFeature failed", error)
            return Result.failure(error)
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: java.io.IOException) {
        NarsLogger.e(TAG, "updateFeature failed", e)
        Result.failure(e)
    }

    suspend fun deleteFeature(featureId: String): Result<Unit> = try {
        val response =
            authenticatedRequest {
                httpClient.delete("$baseUrl/api/features/$featureId") {
                    authHeaders().forEach { (k, v) -> headers.append(k, v) }
                }
            }
        if (!response.status.isSuccess()) {
            val error = Exception("Delete failed: HTTP ${response.status.value}")
            NarsLogger.e(TAG, "deleteFeature failed", error)
            return Result.failure(error)
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: java.io.IOException) {
        NarsLogger.e(TAG, "deleteFeature failed", e)
        Result.failure(e)
    }
}
