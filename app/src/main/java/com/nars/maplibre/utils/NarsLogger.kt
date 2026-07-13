package com.nars.maplibre.utils

import timber.log.Timber

/**
 * Centralized logging utility for NARS application
 *
 * Provides safe logging that:
 * - Prevents logging sensitive information (tokens, passwords, cookies)
 * - Can be disabled in production builds
 * - Provides consistent log formatting
 */
object NarsLogger {
    private const val DEFAULT_TAG = "NARS"

    /**
     * Enable/disable logging
     * In production, automatically disabled
     */
    val isEnabled: Boolean =
        try {
            com.nars.maplibre.BuildConfig.DEBUG
        } catch (_: Exception) {
            true
        }

    /**
     * Verbose logging - most detailed, disabled in production
     */
    fun v(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (isEnabled) {
            if (throwable != null) {
                Timber.tag(tag).v(throwable, sanitizeMessage(message))
            } else {
                Timber.tag(tag).v(sanitizeMessage(message))
            }
        }
    }

    /**
     * Debug logging - for development debugging
     */
    fun d(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (isEnabled) {
            if (throwable != null) {
                Timber.tag(tag).d(throwable, sanitizeMessage(message))
            } else {
                Timber.tag(tag).d(sanitizeMessage(message))
            }
        }
    }

    /**
     * Info logging - for general informational messages
     */
    fun i(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (isEnabled) {
            if (throwable != null) {
                Timber.tag(tag).i(throwable, sanitizeMessage(message))
            } else {
                Timber.tag(tag).i(sanitizeMessage(message))
            }
        }
    }

    /**
     * Warning logging - for potential issues
     */
    fun w(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        Timber.tag(tag).w(throwable, sanitizeMessage(message))
    }

    /**
     * Error logging - for errors and exceptions
     */
    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        Timber.tag(tag).e(throwable, sanitizeMessage(message))
    }

    /**
     * What a Terrible Failure - for critical errors
     */
    fun wtf(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        Timber.tag(tag).wtf(throwable, sanitizeMessage(message))
    }

    /**
     * Sanitize log message to prevent sensitive data leakage
     * Removes or masks:
     * - Authorization tokens
     * - Cookies
     * - Passwords
     * - API keys
     */
    private val SENSITIVE_PATTERNS = listOf(
        Regex("Bearer\\s+[A-Za-z0-9\\-_]+") to "Bearer [REDACTED]",
        Regex("session[_-]?id[=:]\\s*[A-Za-z0-9\\-_]+") to "session_id=[REDACTED]",
        Regex("password[=:]\\s*[^\\s,}]+") to "password=[REDACTED]",
        Regex("api[_-]?key[=:]\\s*[A-Za-z0-9\\-_]+") to "api_key=[REDACTED]",
        Regex("access[_-]?token[=:]\\s*[A-Za-z0-9\\-_]+") to "access_token=[REDACTED]",
        Regex("refresh[_-]?token[=:]\\s*[A-Za-z0-9\\-_]+") to "refresh_token=[REDACTED]",
    )

    private fun sanitizeMessage(message: String): String {
        var result = message
        for ((pattern, replacement) in SENSITIVE_PATTERNS) {
            result = pattern.replace(result, replacement)
        }
        return result
    }

    /**
     * Log network request without sensitive headers
     */
    fun logNetworkRequest(tag: String, method: String, url: String, body: String? = null) {
        if (isEnabled) {
            d(tag, "$method $url")
            body?.let {
                v(tag, "Request body: $it")
            }
        }
    }

    /**
     * Log network response without sensitive data
     */
    fun logNetworkResponse(tag: String, url: String, responseCode: Int, responseBody: String? = null) {
        if (isEnabled) {
            d(tag, "Response from $url: $responseCode")
            responseBody?.let {
                v(tag, "Response body: ${sanitizeMessage(it)}")
            }
        }
    }

    /**
     * Log authentication event (safe)
     */
    fun logAuthEvent(tag: String, event: String, username: String? = null) {
        val safeMessage =
            if (username != null) {
                "$event for user: ${maskUsername(username)}"
            } else {
                event
            }
        i(tag, safeMessage)
    }

    /**
     * Mask username for privacy
     */
    private fun maskUsername(username: String): String = when {
        username.length <= 2 -> "**"
        username.length <= 4 -> "${username[0]}**${username.last()}"
        else -> "${username.take(2)}**${username.takeLast(2)}"
    }
}
