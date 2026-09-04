package com.nars.maplibre.utils

import org.junit.Assert.assertTrue
import org.junit.Test

class TileSourcesTest {
    @Test
    fun `tile URLs are non-empty`() {
        assertTrue(TileSources.SATELLITE.isNotBlank())
        assertTrue(TileSources.STREET.isNotBlank())
        assertTrue(TileSources.LIGHT.isNotBlank())
        assertTrue(TileSources.DARK.isNotBlank())
    }

    @Test
    fun `tile URLs contain zyx placeholders`() {
        listOf(TileSources.SATELLITE, TileSources.STREET, TileSources.LIGHT, TileSources.DARK).forEach { url ->
            assertTrue("URL must contain {z}: $url", url.contains("{z}"))
            assertTrue("URL must contain {x}: $url", url.contains("{x}"))
            assertTrue("URL must contain {y}: $url", url.contains("{y}"))
        }
    }

    @Test
    fun `style version is positive`() {
        assertTrue(TileSources.STYLE_VERSION > 0)
    }

    @Test
    fun `tile size is positive`() {
        assertTrue(TileSources.TILE_SIZE > 0)
    }

    @Test
    fun `max zoom is positive`() {
        assertTrue(TileSources.MAP_MAX_ZOOM > 0)
    }

    @Test
    fun `glyphs URL is non-empty`() {
        assertTrue(TileSources.GLYPHS.isNotBlank())
    }

    @Test
    fun `attribution strings are non-empty`() {
        listOf(TileSources.ATTR_ESRI, TileSources.ATTR_OSM, TileSources.ATTR_CARTO).forEach { attr ->
            assertTrue("Attribution should be non-empty: $attr", attr.isNotBlank())
        }
    }
}
