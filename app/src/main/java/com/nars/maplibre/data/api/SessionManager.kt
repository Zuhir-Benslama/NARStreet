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
     * Local state is always cleared (defensive); the returned Result lets
     * callers distinguish "fully logged out" from "logged out locally but the
     * server revocation failed".
     */
    suspend fun logout(): Result<Unit> {
        val result = apiService.logout()
        result.onFailure { e ->
            NarsLogger.w(TAG, "Server-side logout failed — local session still cleared", e)
        }
        appPreferences.authToken = null
        appPreferences.refreshToken = null
        appPreferences.user = null
        appPreferences.municipalityName = null
        apiService.setSessionToken(null)
        apiService.setRefreshToken(null)
        return result
    }

    fun getUser() = appPreferences.user

    fun getMunicipalityName() = appPreferences.municipalityName
}
