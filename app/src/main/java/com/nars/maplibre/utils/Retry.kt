package com.nars.maplibre.utils

import kotlinx.coroutines.delay
import java.io.IOException

private const val RETRY_ATTEMPTS = 3
private const val RETRY_BACKOFF_MS = 1000L

/**
 * Retries a suspend operation on transient network failures (IOException).
 *
 * Non-transient failures (auth, serialization, HTTP errors) are returned
 * immediately. Delays grow linearly with the attempt number (1x, 2x, ...).
 */
internal suspend fun <T> retryOnTransientFailure(
    attempts: Int = RETRY_ATTEMPTS,
    backoffMs: Long = RETRY_BACKOFF_MS,
    block: suspend () -> Result<T>,
): Result<T> {
    var attempt = 1
    var result = block()
    while (result.isFailure && attempt < attempts) {
        val error = result.exceptionOrNull()
        if (error !is IOException) break
        delay(backoffMs * attempt)
        attempt++
        result = block()
    }
    return result
}
