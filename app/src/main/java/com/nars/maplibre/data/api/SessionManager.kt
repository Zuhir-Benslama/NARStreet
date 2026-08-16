package com.nars.maplibre.data.api

import com.nars.maplibre.AppPreferences
import com.nars.maplibre.data.model.LoginResponse
import com.nars.maplibre.utils.NarsLogger

class SessionManager(private val apiService: ApiService, private val appPreferences: AppPreferences) {
    companion object {
        private const val TAG = "SessionManager"
    }

    fun isLoggedIn(): Boolean = appPreferences.isLoggedIn

    suspend fun login(username: String, password: String): Result<LoginResponse> {
        val result = apiService.login(username, password)
        result.onSuccess { response ->
            appPreferences.user =
                response.user.copy(
                    username = username,
                    name = response.user.name.ifBlank { username },
                )
            appPreferences.municipalityName = response.municipalityName
            NarsLogger.logAuthEvent(TAG, "Session created", username)
        }
        return result
    }

    /**
     * Logs out locally and reports the server-side revocation outcome.
     * Local state is always cleared (defensive, even if the server call throws
     * unexpectedly); the returned Result lets callers distinguish "fully logged
     * out" from "logged out locally but the server revocation failed".
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun logout(): Result<Unit> {
        val result =
            try {
                apiService.logout()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                NarsLogger.w(TAG, "Server-side logout threw — local session still cleared", e)
                Result.failure(e)
            }
        result.onFailure { e ->
            NarsLogger.w(TAG, "Server-side logout failed — local session still cleared", e)
        }
        clearLocalSession()
        return result
    }

    private fun clearLocalSession() {
        appPreferences.authToken = null
        appPreferences.refreshToken = null
        appPreferences.user = null
        appPreferences.municipalityName = null
        apiService.setSessionToken(null)
        apiService.setRefreshToken(null)
    }

    fun getUser() = appPreferences.user

    fun getMunicipalityName() = appPreferences.municipalityName
}
