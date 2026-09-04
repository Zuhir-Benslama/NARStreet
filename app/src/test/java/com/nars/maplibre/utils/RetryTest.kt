package com.nars.maplibre.utils

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RetryTest {
    @Test
    fun `retries transient failures until success`() = runTest {
        var calls = 0
        val result =
            retryOnTransientFailure(attempts = 3, backoffMs = 1) {
                calls++
                if (calls < 3) {
                    Result.failure(IOException("boom"))
                } else {
                    Result.success(calls)
                }
            }

        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrNull())
    }

    @Test
    fun `does not retry non transient failures`() = runTest {
        var calls = 0
        val result =
            retryOnTransientFailure(attempts = 3, backoffMs = 1) {
                calls++
                Result.failure<Int>(RuntimeException("auth"))
            }

        assertTrue(result.isFailure)
        assertEquals(1, calls)
    }

    @Test
    fun `exhausts attempts then returns last failure`() = runTest {
        var calls = 0
        val result =
            retryOnTransientFailure(attempts = 3, backoffMs = 1) {
                calls++
                Result.failure<Int>(IOException("boom"))
            }

        assertTrue(result.isFailure)
        assertEquals(3, calls)
    }

    @Test
    fun `single attempt succeeds on first try`() = runTest {
        val result = retryOnTransientFailure(attempts = 1, backoffMs = 1) {
            Result.success(42)
        }

        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `single attempt fails`() = runTest {
        var calls = 0
        val result = retryOnTransientFailure(attempts = 1, backoffMs = 1) {
            calls++
            Result.failure<Int>(IOException("boom"))
        }

        assertTrue(result.isFailure)
        assertEquals(1, calls)
    }

    @Test
    fun `zero attempts still calls block once`() = runTest {
        var calls = 0
        val result = retryOnTransientFailure(attempts = 0, backoffMs = 1) {
            calls++
            Result.success(42)
        }

        assertTrue(result.isSuccess)
        assertEquals(1, calls)
        assertEquals(42, result.getOrNull())
    }
}
