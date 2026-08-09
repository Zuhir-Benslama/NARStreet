package com.nars.maplibre.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigTest {
    @Test
    fun `tile URLs fall back to bundled providers when unset`() {
        assertTrue(Config.TILE_SATELLITE.endsWith(SATELLITE_PATH))
        assertTrue(Config.TILE_STREET.endsWith(STREET_PATH))
        assertTrue(Config.TILE_LIGHT.endsWith(LIGHT_PATH))
        assertTrue(Config.TILE_DARK.endsWith(DARK_PATH))
    }

    @Test
    fun `constants expose expected defaults`() {
        assertEquals(DEFAULT_TIMEOUT_MS, Config.API_DEFAULT_TIMEOUT_MS)
        assertEquals(MAX_RETRIES, Config.API_MAX_RETRIES)
        assertEquals(MAX_ZOOM, Config.MAP_MAX_ZOOM)
        assertEquals(TILE_SIZE, Config.TILE_SIZE)
        assertEquals(STYLE_VERSION, Config.STYLE_VERSION)
    }

    private companion object {
        const val SATELLITE_PATH = "World_Imagery/MapServer/tile/{z}/{y}/{x}"
        const val STREET_PATH = "tile.openstreetmap.org/{z}/{x}/{y}.png"
        const val LIGHT_PATH = "basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png"
        const val DARK_PATH = "basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png"
        const val DEFAULT_TIMEOUT_MS = 15000
        const val MAX_RETRIES = 3
        const val MAX_ZOOM = 19
        const val TILE_SIZE = 256
        const val STYLE_VERSION = 8
    }
}
