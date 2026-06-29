package com.nars.maplibre.data.api

import com.nars.maplibre.AppPreferences
import com.nars.maplibre.BuildConfig
import com.nars.maplibre.data.model.LoginApiResponse
import com.nars.maplibre.data.model.LoginRequest
import com.nars.maplibre.data.model.LoginResponse
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.User
import com.nars.maplibre.utils.NarsLogger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject

class ApiService(private val httpClient: HttpClient, private val preferences: AppPreferences) {
    companion object {
        private const val TAG = "ApiService"
        private const val LOGIN_TIMEOUT_MS = 15000
    }

    private val baseUrl: String = BuildConfig.API_BASE_URL.trimEnd('/')

    @Volatile private var authToken: String? = null

    @Volatile private var cookie: String? = null

    fun setAuthToken(token: String?) {
        authToken = token
    }

    fun getAuthToken(): String? = authToken

    fun setCookie(c: String?) {
        cookie = c
    }

    fun getCookie(): String? = cookie

    private fun extractAndSetCookie(response: io.ktor.client.statement.HttpResponse) {
        val cookieHeader = response.headers[HttpHeaders.SetCookie]
        cookieHeader?.let { rawCookie ->
            val tokenMatch = Regex("access_token=([^;]+)").find(rawCookie)
            tokenMatch?.let { match ->
                cookie = match.groupValues[1]
            }
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

    private fun authHeaders(): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        authToken?.let { headers["Authorization"] = "Bearer $it" }
        cookie?.let { headers["Cookie"] = it }
        return headers
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

            extractAndSetCookie(response)

            if (!response.status.isSuccess()) {
                val body = response.bodyAsText()
                val errorResponse =
                    try {
                        apiJson.decodeFromString<LoginApiResponse>(body)
                    } catch (_: kotlinx.serialization.SerializationException) {
                        null
                    }
                return Result.failure(
                    Exception(errorResponse?.message ?: "Login failed: HTTP ${response.status.value}"),
                )
            }

            val body = response.bodyAsText()
            val apiResponse = apiJson.decodeFromString<LoginApiResponse>(body)

            if (!apiResponse.success) {
                return Result.failure(Exception(apiResponse.message ?: "Login failed"))
            }

            val token = apiResponse.token ?: apiResponse.accessToken
            token?.let { authToken = it }

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

    suspend fun logout(): Result<Unit> = try {
        httpClient.post("$baseUrl/api/logout") {
            authHeaders().forEach { (k, v) -> headers.append(k, v) }
            contentType(ContentType.Application.Json)
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: java.io.IOException) {
        NarsLogger.e(TAG, "Logout failed", e)
        Result.failure(e)
    }

    suspend fun loadFeatures(): Result<List<NarsFeature>> {
        return try {
            val response =
                httpClient.get("$baseUrl/api/load") {
                    authHeaders().forEach { (k, v) -> headers.append(k, v) }
                }
            val body = response.bodyAsText()
            if (body.isBlank()) return Result.success(emptyList())
            val jsonElement = apiJson.parseToJsonElement(body)
            val items =
                if (jsonElement is JsonObject) {
                    val apiResponse = apiJson.decodeFromJsonElement(ApiFeatureListResponse.serializer(), jsonElement)
                    apiResponse.features ?: emptyList()
                } else {
                    apiJson.decodeFromJsonElement(
                        kotlinx.serialization.builtins.ListSerializer(ApiFeatureResponse.serializer()),
                        jsonElement,
                    )
                }
            Result.success(items.mapNotNull { it.toNarsFeature() })
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
        val requestBody = apiJson.encodeToString(feature.toSaveFeatureRequest())
        val response =
            httpClient.post("$baseUrl/api/save") {
                authHeaders().forEach { (k, v) -> headers.append(k, v) }
                setBody(requestBody)
            }
        val id =
            apiJson.decodeFromString<SaveFeatureResponse>(response.bodyAsText()).id
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
        val requestBody = apiJson.encodeToString(feature.toSaveFeatureRequest())
        httpClient.put("$baseUrl/api/update/$featureId") {
            authHeaders().forEach { (k, v) -> headers.append(k, v) }
            setBody(requestBody)
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: java.io.IOException) {
        NarsLogger.e(TAG, "updateFeature failed", e)
        Result.failure(e)
    }

    suspend fun deleteFeature(featureId: String): Result<Unit> = try {
        httpClient.delete("$baseUrl/api/delete/$featureId") {
            authHeaders().forEach { (k, v) -> headers.append(k, v) }
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: java.io.IOException) {
        NarsLogger.e(TAG, "deleteFeature failed", e)
        Result.failure(e)
    }
}
