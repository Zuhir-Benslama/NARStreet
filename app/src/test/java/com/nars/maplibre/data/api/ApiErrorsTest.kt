package com.nars.maplibre.data.api

import com.nars.maplibre.utils.Config
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ApiErrorsTest {
    @Test
    fun `withRetry succeeds on first attempt`() = runTest {
        var callCount = 0
        val result =
            withRetry(
                operation = {
                    callCount++
                    "success"
                },
                config = RetryConfig(maxRetries = 3),
            )
        assertEquals("success", result)
        assertEquals(1, callCount)
    }

    @Test
    fun `withRetry retries on failure`() = runTest {
        var callCount = 0
        val result =
            withRetry(
                operation = {
                    callCount++
                    if (callCount < 3) {
                        throw IllegalStateException("transient failure")
                    } else {
                        "success"
                    }
                },
                config = RetryConfig(maxRetries = 3, baseDelayMs = 1, maxDelayMs = 10),
            )
        assertEquals("success", result)
        assertEquals(3, callCount)
    }

    @Test
    fun `withRetry throws after exhausting retries`() = runTest {
        var callCount = 0
        try {
            withRetry(
                operation = {
                    callCount++
                    throw IllegalStateException("persistent failure")
                },
                config = RetryConfig(maxRetries = 2, baseDelayMs = 1, maxDelayMs = 10),
            )
            fail("Expected IllegalStateException")
        } catch (_: IllegalStateException) {
            assertEquals(3, callCount)
        }
    }

    @Test
    fun `withRetry does not retry on AuthError`() = runTest {
        var callCount = 0
        val ctx = NarsError.ErrorContext(url = "/api/test", method = "GET")
        try {
            withRetry(
                operation = {
                    callCount++
                    throw AuthError(ctx)
                },
                config = RetryConfig(maxRetries = 3, baseDelayMs = 1, maxDelayMs = 10),
            )
            fail("Expected AuthError")
        } catch (_: AuthError) {
            assertEquals(1, callCount)
        }
    }

    @Test
    fun `withRetry does not retry on ValidationError`() = runTest {
        var callCount = 0
        val ctx = NarsError.ErrorContext(url = "/api/test", method = "POST")
        try {
            withRetry(
                operation = {
                    callCount++
                    throw ValidationError(ctx)
                },
                config = RetryConfig(maxRetries = 3, baseDelayMs = 1, maxDelayMs = 10),
            )
            fail("Expected ValidationError")
        } catch (_: ValidationError) {
            assertEquals(1, callCount)
        }
    }

    @Test
    fun `withRetry does not retry on NotFoundError`() = runTest {
        var callCount = 0
        val ctx = NarsError.ErrorContext(url = "/api/test", method = "GET")
        try {
            withRetry(
                operation = {
                    callCount++
                    throw NotFoundError(ctx)
                },
                config = RetryConfig(maxRetries = 3, baseDelayMs = 1, maxDelayMs = 10),
            )
            fail("Expected NotFoundError")
        } catch (_: NotFoundError) {
            assertEquals(1, callCount)
        }
    }

    @Test
    fun `withRetry retries on NetworkError`() = runTest {
        var callCount = 0
        val ctx = NarsError.ErrorContext(url = "/api/test", method = "GET")
        try {
            withRetry(
                operation = {
                    callCount++
                    throw NetworkError(ctx)
                },
                config = RetryConfig(maxRetries = 2, baseDelayMs = 1, maxDelayMs = 10),
            )
            fail("Expected NetworkError")
        } catch (_: NetworkError) {
            assertTrue(callCount > 1)
        }
    }

    @Test
    fun `withRetry retries on ServerError`() = runTest {
        var callCount = 0
        val ctx = NarsError.ErrorContext(url = "/api/test", method = "GET")
        try {
            withRetry(
                operation = {
                    callCount++
                    throw ServerError(ctx)
                },
                config = RetryConfig(maxRetries = 1, baseDelayMs = 1, maxDelayMs = 10),
            )
            fail("Expected ServerError")
        } catch (_: ServerError) {
            assertEquals(2, callCount)
        }
    }

    @Test
    fun `withRetry calls onRetry callback`() = runTest {
        var retryCount = 0
        val ctx = NarsError.ErrorContext(url = "/api/test", method = "GET")
        try {
            withRetry(
                operation = { throw ServerError(ctx) },
                config = RetryConfig(maxRetries = 2, baseDelayMs = 1, maxDelayMs = 10),
                onRetry = { _, attempt -> retryCount = attempt },
            )
            fail("Expected ServerError")
        } catch (_: ServerError) {
            assertTrue(retryCount > 0)
        }
    }

    @Test
    fun `RetryConfig has expected defaults`() {
        assertEquals(Config.API_MAX_RETRIES, RetryConfig().maxRetries)
        assertEquals(Config.API_RETRY_BASE_DELAY_MS.toLong(), RetryConfig().baseDelayMs)
        assertEquals(Config.API_RETRY_MAX_DELAY_MS.toLong(), RetryConfig().maxDelayMs)
        assertEquals(0.2, RetryConfig().jitterFactor, 0.001)
    }
}
