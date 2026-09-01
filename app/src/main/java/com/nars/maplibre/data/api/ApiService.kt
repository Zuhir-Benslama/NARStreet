package com.nars.maplibre.data.api

import com.nars.maplibre.AppPreferences
import com.nars.maplibre.BuildConfig
import com.nars.maplibre.data.model.LoginApiResponse
import com.nars.maplibre.data.model.LoginResponse
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.User
import com.nars.maplibre.utils.NarsLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
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

/**
 * HTTP transport for the NARS API. Owns token rotation and session-expiry
 * signalling; token storage itself lives in [SessionTokens].
 */
class ApiService(private val httpClient: HttpClient, private val preferences: AppPreferences) {
    companion object {
        private const val TAG = "ApiService"

        /** Max rows the backend returns per page (clamped server-side). */
        private const val FEATURES_PAGE_SIZE = 500
    }

    private val baseUrl: String = BuildConfig.API_BASE_URL.trimEnd('/')

    val tokens = SessionTokens(preferences)

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

    /** Attaches the current bearer token to a request, when one is set. */
    private fun HttpRequestBuilder.applyAuthHeaders() {
        tokens.getSessionToken()?.let { token ->
            header(HttpHeaders.Authorization, "Bearer $token")
        }
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

    /**
     * Executes an authenticated request, transparently refreshing the access
     * token once when the server responds 401 (access token expiry).
     */
    private suspend fun authenticatedRequest(block: suspend () -> HttpResponse): HttpResponse {
        val accessTokenBeforeRequest = tokens.getSessionToken()
        var response = block()
        if (response.status == HttpStatusCode.Unauthorized) {
            val refreshed =
                refreshMutex.withLock {
                    if (tokens.getSessionToken() != accessTokenBeforeRequest) {
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
        val token = tokens.getRefreshToken() ?: preferences.refreshToken ?: return false
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
                tokens.clear()
                _sessionExpired.tryEmit(Unit)
                return false
            }
            if (!response.status.isSuccess()) {
                NarsLogger.w(TAG, "Token refresh failed (HTTP ${response.status.value}) — keeping session for retry")
                return false
            }
            val accessIssued = tokens.adoptCookies(response)
            tokens.persist()
            accessIssued
        } catch (e: CancellationException) {
            throw e
        } catch (e: java.io.IOException) {
            NarsLogger.w(TAG, "Token refresh failed", e)
            false
        }
    }

    /**
     * Runs an authenticated request, maps a non-2xx response (and any I/O or
     * deserialization error during the request or [onSuccess] mapping) into a
     * failed [Result]. Cancellation is always rethrown.
     */
    private suspend fun <T> executeRequest(
        action: String,
        request: suspend () -> HttpResponse,
        onSuccess: suspend (HttpResponse) -> T,
    ): Result<T> = try {
        val response = authenticatedRequest(request)
        if (!response.status.isSuccess()) {
            val error = Exception("$action failed: HTTP ${response.status.value}")
            NarsLogger.e(TAG, "$action failed", error)
            Result.failure(error)
        } else {
            Result.success(onSuccess(response))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: kotlinx.serialization.SerializationException) {
        NarsLogger.e(TAG, "$action failed", e)
        Result.failure(e)
    } catch (e: java.io.IOException) {
        NarsLogger.e(TAG, "$action failed", e)
        Result.failure(e)
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
            tokens.adoptCookies(response)

            val token = apiResponse.token ?: apiResponse.accessToken
            token?.let { tokens.setSessionToken(it) }

            tokens.persist()

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

    suspend fun logout(): Result<Unit> = executeRequest("Logout", {
        httpClient.post("$baseUrl/api/logout") {
            applyAuthHeaders()
            contentType(ContentType.Application.Json)
        }
    }) {}

    private suspend fun fetchFeaturesPage(skip: Int): Result<ApiLoadFeaturesResponse?> = executeRequest("Load", {
        httpClient.get("$baseUrl/api/features") {
            applyAuthHeaders()
            parameter("skip", skip)
            parameter("take", FEATURES_PAGE_SIZE)
        }
    }) { response ->
        response.bodyAsText().ifBlank { null }?.let {
            apiJson.decodeFromString<ApiLoadFeaturesResponse>(it)
        }
    }

    suspend fun loadFeatures(): Result<List<NarsFeature>> {
        val allFeatures = mutableListOf<NarsFeature>()
        var skip = 0
        var hasMore = true
        while (hasMore) {
            val page = fetchFeaturesPage(skip).getOrElse { return Result.failure(it) }
            if (page == null) {
                hasMore = false
            } else {
                val features = page.features
                allFeatures += features.mapNotNull { it.toNarsFeature() }
                skip += features.size
                hasMore = features.isNotEmpty() && page.count > skip
            }
        }
        return Result.success(allFeatures)
    }

    suspend fun saveFeature(feature: NarsFeature): Result<String> = executeRequest("Save", {
        httpClient.post("$baseUrl/api/features") {
            applyAuthHeaders()
            contentType(ContentType.Application.Json)
            setBody(apiJson.encodeToString(feature.toApiSaveRequest()))
        }
    }) { response ->
        apiJson.decodeFromString<ApiSaveFeatureResponse>(response.bodyAsText()).id
            ?: feature.id
    }

    suspend fun updateFeature(featureId: String, feature: NarsFeature): Result<Unit> = executeRequest("Update", {
        httpClient.put("$baseUrl/api/features/$featureId") {
            applyAuthHeaders()
            contentType(ContentType.Application.Json)
            setBody(apiJson.encodeToString(feature.toApiUpdateRequest()))
        }
    }) {}

    suspend fun deleteFeature(featureId: String): Result<Unit> = executeRequest("Delete", {
        httpClient.delete("$baseUrl/api/features/$featureId") {
            applyAuthHeaders()
        }
    }) {}
}
