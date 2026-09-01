package com.nars.maplibre.data.api

import com.nars.maplibre.AppPreferences
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders

/**
 * Owns the in-memory session tokens (access + refresh) and their persistence
 * into [AppPreferences]. Every read-modify-write runs under a single lock so a
 * background-triggered in-memory clear (see NarsApplication) is atomic against
 * concurrent cookie writes from refresh/login. Extracted from ApiService so the
 * transport layer is only responsible for requests, not token bookkeeping.
 */
class SessionTokens(private val preferences: AppPreferences) {
    companion object {
        private val COOKIE_ACCESS_TOKEN_REGEX = Regex("access_token=([^;]+)")
        private val COOKIE_REFRESH_TOKEN_REGEX = Regex("refresh_token=([^;]+)")
    }

    private val tokenLock = Any()

    @Volatile private var sessionToken: String? = null

    @Volatile private var refreshToken: String? = null

    fun setSessionToken(token: String?) {
        synchronized(tokenLock) {
            sessionToken = token
        }
    }

    fun getSessionToken(): String? = sessionToken

    fun setRefreshToken(token: String?) {
        synchronized(tokenLock) {
            refreshToken = token
        }
    }

    fun getRefreshToken(): String? = refreshToken

    /**
     * Drops the in-memory tokens WITHOUT touching persisted storage. Used by
     * the app-lifecycle security policy (NarsApplication): when the app is
     * backgrounded the tokens must leave memory, but stay encrypted in prefs so
     * the foreground restore can bring them back.
     */
    fun clearInMemoryTokens() {
        synchronized(tokenLock) {
            sessionToken = null
            refreshToken = null
        }
    }

    /**
     * Extracts the access + refresh token cookies issued by the backend (both
     * are set on signin and on every /api/refresh). Returns true when an
     * access-token cookie was present, so callers can distinguish "a fresh
     * access token was issued" from "nothing changed".
     */
    fun adoptCookies(response: HttpResponse): Boolean {
        var sawAccessToken = false
        synchronized(tokenLock) {
            response.headers.getAll(HttpHeaders.SetCookie)?.forEach { rawCookie ->
                COOKIE_ACCESS_TOKEN_REGEX.find(rawCookie)?.let { match ->
                    sessionToken = match.groupValues[1]
                    sawAccessToken = true
                }
                COOKIE_REFRESH_TOKEN_REGEX.find(rawCookie)?.let { match ->
                    refreshToken = match.groupValues[1]
                }
            }
        }
        return sawAccessToken
    }

    /** Persists the current in-memory tokens so the session survives process death. */
    fun persist() {
        synchronized(tokenLock) {
            preferences.authToken = sessionToken
            preferences.refreshToken = refreshToken
        }
    }

    /** Drops the session entirely — used when the backend rejects a refresh or on logout. */
    fun clear() {
        synchronized(tokenLock) {
            sessionToken = null
            refreshToken = null
            persist()
        }
    }
}
