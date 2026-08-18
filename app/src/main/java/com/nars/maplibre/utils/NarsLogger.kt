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
     * Whether verbose/debug/info logging is enabled.
     * Production builds keep these off (chatty, but harmless) while warnings,
     * errors and failures are always logged — they are already sanitized and are
     * essential for diagnosing production issues.
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
     * Warning logging - for potential issues (also enabled in production)
     */
    fun w(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        log(Level.WARNING, tag, message, throwable)
    }

    /**
     * Error logging - for errors and exceptions (also enabled in production)
     */
    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        log(Level.ERROR, tag, message, throwable)
    }

    /**
     * What a Terrible Failure - for critical errors (also enabled in production)
     */
    fun wtf(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        log(Level.WTF, tag, message, throwable)
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
        val tree = Timber.tag(tag)
        if (throwable != null) {
            val logThrowable = sanitizeThrowable(throwable, safeMessage)
            when (level) {
                Level.VERBOSE -> tree.v(logThrowable)
                Level.DEBUG -> tree.d(logThrowable)
                Level.INFO -> tree.i(logThrowable)
                Level.WARNING -> tree.w(logThrowable)
                Level.ERROR -> tree.e(logThrowable)
                Level.WTF -> tree.wtf(logThrowable)
            }
        } else {
            when (level) {
                Level.VERBOSE -> tree.v(safeMessage)
                Level.DEBUG -> tree.d(safeMessage)
                Level.INFO -> tree.i(safeMessage)
                Level.WARNING -> tree.w(safeMessage)
                Level.ERROR -> tree.e(safeMessage)
                Level.WTF -> tree.wtf(safeMessage)
            }
        }
    }

    /**
     * Returns a throwable whose message is [displayMessage] (already sanitized
     * by the caller) and whose cause is the original [throwable] with any
     * sensitive data in its own message redacted.
     */
    private fun sanitizeThrowable(throwable: Throwable, displayMessage: String): Throwable {
        val rawMessage = throwable.message
        val safeCause = rawMessage?.let(::sanitizeMessage)
        val cause = if (safeCause != null && safeCause != rawMessage) {
            RuntimeException(safeCause).also {
                it.setStackTrace(throwable.stackTrace)
                it.initCause(throwable.cause)
            }
        } else {
            throwable
        }
        return RuntimeException(displayMessage, cause)
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
