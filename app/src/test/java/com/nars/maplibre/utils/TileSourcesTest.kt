package com.nars.maplibre.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileSourcesTest {
    @Test
    fun `tile URLs fall back to bundled providers when unset`() {
        assertTrue(TileSources.SATELLITE.endsWith(SATELLITE_PATH))
        assertTrue(TileSources.STREET.endsWith(STREET_PATH))
        assertTrue(TileSources.LIGHT.endsWith(LIGHT_PATH))
        assertTrue(TileSources.DARK.endsWith(DARK_PATH))
    }

    @Test
    fun `style constants expose expected defaults`() {
        assertEquals(MAX_ZOOM, TileSources.MAP_MAX_ZOOM)
        assertEquals(TILE_SIZE, TileSources.TILE_SIZE)
        assertEquals(STYLE_VERSION, TileSources.STYLE_VERSION)
    }

    private companion object {
        const val SATELLITE_PATH = "World_Imagery/MapServer/tile/{z}/{y}/{x}"
        const val STREET_PATH = "tile.openstreetmap.org/{z}/{x}/{y}.png"
        const val LIGHT_PATH = "basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png"
        const val DARK_PATH = "basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png"
        const val MAX_ZOOM = 19
        const val TILE_SIZE = 256
        const val STYLE_VERSION = 8
    }
}
