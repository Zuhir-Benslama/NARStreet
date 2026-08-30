package com.nars.maplibre.utils

import kotlinx.coroutines.delay
import java.io.IOException

/**
 * Retries a suspend operation on transient network failures (IOException).
 *
 * Non-transient failures (auth, serialization, HTTP errors) are returned
 * immediately. Delays grow linearly with the attempt number (1x, 2x, ...),
 * capped at [Config.API_RETRY_MAX_DELAY_MS] so a long chain of retries never
 * stalls the caller indefinitely.
 */
internal suspend fun <T> retryOnTransientFailure(
    attempts: Int = Config.API_MAX_RETRIES,
    backoffMs: Long = Config.API_RETRY_BASE_DELAY_MS.toLong(),
    maxDelayMs: Long = Config.API_RETRY_MAX_DELAY_MS.toLong(),
    block: suspend () -> Result<T>,
): Result<T> {
    var attempt = 1
    var result = block()
    while (result.isFailure && attempt < attempts) {
        val error = result.exceptionOrNull()
        if (error !is IOException) break
        delay((backoffMs * attempt).coerceAtMost(maxDelayMs))
        attempt++
        result = block()
    }
    return result
}
