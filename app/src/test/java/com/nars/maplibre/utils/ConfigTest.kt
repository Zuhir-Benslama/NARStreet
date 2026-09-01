package com.nars.maplibre.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigTest {
    @Test
    fun `constants expose expected defaults`() {
        assertEquals(DEFAULT_TIMEOUT_MS, Config.API_DEFAULT_TIMEOUT_MS)
        assertEquals(MAX_RETRIES, Config.API_MAX_RETRIES)
        assertEquals(RETRY_BASE_DELAY_MS, Config.API_RETRY_BASE_DELAY_MS.toInt())
        assertEquals(RETRY_MAX_DELAY_MS, Config.API_RETRY_MAX_DELAY_MS.toInt())
    }

    @Test
    fun `retry delays start below the max cap`() {
        assertTrue(Config.API_RETRY_BASE_DELAY_MS < Config.API_RETRY_MAX_DELAY_MS)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 15000
        const val MAX_RETRIES = 3
        const val RETRY_BASE_DELAY_MS = 1000
        const val RETRY_MAX_DELAY_MS = 10000
    }
}
