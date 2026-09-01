package com.nars.maplibre.utils

import com.nars.maplibre.BuildConfig

/**
 * Map style sources: raster tile providers, glyph endpoint, and attribution
 * strings. Base tile URLs can be overridden per build via local.properties
 * (TILE_*); each falls back to a bundled public provider so debug builds work
 * out of the box. Kept separate from [Config], which holds app API/camera
 * constants.
 */
object TileSources {
    val SATELLITE: String =
        BuildConfig.TILE_SATELLITE.ifBlank {
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
        }

    val STREET: String =
        BuildConfig.TILE_STREET.ifBlank {
            "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        }

    val LIGHT: String =
        BuildConfig.TILE_LIGHT.ifBlank {
            "https://basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png"
        }

    val DARK: String =
        BuildConfig.TILE_DARK.ifBlank {
            "https://basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png"
        }

    const val GLYPHS = "https://fonts.openmaptiles.org/{fontstack}/{range}.pbf"
    const val STYLE_VERSION = 8
    const val TILE_SIZE = 256
    const val MAP_MAX_ZOOM = 19

    const val ATTR_ESRI = "Source: Esri, Maxar, Earthstar Geographics, and the GIS User Community"
    const val ATTR_OSM = "© OpenStreetMap contributors"
    const val ATTR_CARTO = "© OpenStreetMap contributors, © CARTO"
}
