package com.nars.maplibre.utils

import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigTest {
    @Test
    fun `API timeout is positive`() {
        assertTrue(Config.API_DEFAULT_TIMEOUT_MS > 0)
    }

    @Test
    fun `connect timeout is positive`() {
        assertTrue(Config.API_CONNECT_TIMEOUT_MS > 0)
    }

    @Test
    fun `retry count is non-negative`() {
        assertTrue(Config.API_MAX_RETRIES >= 0)
    }

    @Test
    fun `retry base delay is less than max delay`() {
        assertTrue(Config.API_RETRY_BASE_DELAY_MS < Config.API_RETRY_MAX_DELAY_MS)
    }

    @Test
    fun `map default latitude is within valid range`() {
        assertTrue(Config.MAP_DEFAULT_LAT in -90.0..90.0)
    }

    @Test
    fun `map default longitude is within valid range`() {
        assertTrue(Config.MAP_DEFAULT_LNG in -180.0..180.0)
    }

    @Test
    fun `map default zoom is positive`() {
        assertTrue(Config.MAP_DEFAULT_ZOOM > 0)
    }

    @Test
    fun `snap threshold is positive`() {
        assertTrue(Config.GEOMAN_SNAP_THRESHOLD_PX > 0)
    }

    @Test
    fun `min road length is positive`() {
        assertTrue(Config.MIN_ROAD_LENGTH_METERS > 0)
    }
}
