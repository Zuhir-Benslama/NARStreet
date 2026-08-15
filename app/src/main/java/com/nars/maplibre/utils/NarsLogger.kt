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
    val isEnabled: Boolean = com.nars.maplibre.BuildConfig.DEBUG

    /**
     * Verbose logging - most detailed, disabled in production
     */
    fun v(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (isEnabled) log(Level.VERBOSE, tag, message, throwable)
    }

    /**
     * Debug logging - for development debugging
     */
    fun d(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (isEnabled) log(Level.DEBUG, tag, message, throwable)
    }

    /**
     * Info logging - for general informational messages
     */
    fun i(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (isEnabled) log(Level.INFO, tag, message, throwable)
    }

    /**
     * Warning logging - for potential issues
     */
    fun w(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (isEnabled) log(Level.WARNING, tag, message, throwable)
    }

    /**
     * Error logging - for errors and exceptions
     */
    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (isEnabled) log(Level.ERROR, tag, message, throwable)
    }

    /**
     * What a Terrible Failure - for critical errors
     */
    fun wtf(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (isEnabled) log(Level.WTF, tag, message, throwable)
    }

    private enum class Level {
        VERBOSE,
        DEBUG,
        INFO,
        WARNING,
        ERROR,
        WTF,
    }

    private fun log(level: Level, tag: String, message: String, throwable: Throwable?) {
        val safeMessage = sanitizeMessage(message)
        val safeThrowable = throwable?.let(::sanitizeThrowable)
        val tree = Timber.tag(tag)
        when (level) {
            Level.VERBOSE ->
                if (safeThrowable != null) tree.v(safeThrowable, safeMessage) else tree.v(safeMessage)

            Level.DEBUG ->
                if (safeThrowable != null) tree.d(safeThrowable, safeMessage) else tree.d(safeMessage)

            Level.INFO ->
                if (safeThrowable != null) tree.i(safeThrowable, safeMessage) else tree.i(safeMessage)

            Level.WARNING ->
                if (safeThrowable != null) tree.w(safeThrowable, safeMessage) else tree.w(safeMessage)

            Level.ERROR ->
                if (safeThrowable != null) tree.e(safeThrowable, safeMessage) else tree.e(safeMessage)

            Level.WTF ->
                if (safeThrowable != null) tree.wtf(safeThrowable, safeMessage) else tree.wtf(safeMessage)
        }
    }

    /**
     * Returns the throwable with its message sanitized. When the raw message
     * contains sensitive data (e.g. a SerializationException embedding a raw
     * JSON body) a wrapper preserving the original stack trace is returned,
     * with the original kept as the cause for full diagnostics in a debugger.
     */
    private fun sanitizeThrowable(throwable: Throwable): Throwable {
        val rawMessage = throwable.message ?: return throwable
        val safeMessage = sanitizeMessage(rawMessage)
        return if (safeMessage == rawMessage) {
            throwable
        } else {
            RuntimeException(safeMessage).also {
                it.setStackTrace(throwable.stackTrace)
                it.initCause(throwable)
            }
        }
    }

    /**
     * Sanitize log message to prevent sensitive data leakage
     * Removes or masks:
     * - Authorization tokens
     * - Cookies
     * - Passwords
     * - API keys
     */
    private const val REDACTED = "[REDACTED]"

    /**
     * Sensitive key names matched in both `key=value` and JSON `"key":"value"` forms.
     * Order matters for alternation: longer/variant forms are matched before the
     * bare `token` so `"accessToken"` is never picked up as `"token"`.
     */
    private const val SENSITIVE_KEYS =
        "(password|passwd|auth[_-]?token|id[_-]?token|" +
            "access[_-]?token|refresh[_-]?token|api[_-]?key|apikey|secret|" +
            "session[_-]?id|credential|auth|jwt|cookie|set-cookie|token)"

    private val SENSITIVE_PATTERNS = listOf(
        // JWT Bearer tokens (three dot-separated base64url segments) and opaque tokens.
        Regex("""(?i)Bearer\s+[A-Za-z0-9\-_=.]+\.[A-Za-z0-9\-_=.]+\.[A-Za-z0-9\-_=.]+""") to "Bearer $REDACTED",
        Regex("""(?i)Bearer\s+[A-Za-z0-9\-_=+/]+""") to "Bearer $REDACTED",
        // JSON bodies: "password":"value", "accessToken":"value", etc. Handles
        // escaped quotes inside the value (\") and values containing any chars.
        Regex(
            """(?i)"$SENSITIVE_KEYS"\s*:\s*"(?:\\.|[^"\\])*"""",
        ) to "\"$1\":\"$REDACTED\"",
        // Header / form / query forms: password=value, session_id: value, Cookie: ...
        Regex(
            """(?i)$SENSITIVE_KEYS\s*[=:]\s*[^,\s;}"']+""",
        ) to "$1=$REDACTED",
    )

    /**
     * Redact sensitive values (tokens, passwords, cookies) from a log message.
     * Public so non-NarsLogger sinks (e.g. the Ktor client logger) can reuse it.
     */
    fun sanitizeMessage(message: String): String {
        var result = message
        for ((pattern, replacement) in SENSITIVE_PATTERNS) {
            result = pattern.replace(result, replacement)
        }
        return result
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
