package com.nars.maplibre.utils

/**
 * App-level constants: API timeouts, retry policy, default map camera, and
 * drawing thresholds. Tile/style provider URLs live in [TileSources].
 */
object Config {
    const val API_DEFAULT_TIMEOUT_MS = 15000

    const val API_CONNECT_TIMEOUT_MS = 10000

    const val API_MAX_RETRIES = 3

    const val API_RETRY_BASE_DELAY_MS = 1000

    const val API_RETRY_MAX_DELAY_MS = 10000

    const val MAP_DEFAULT_LNG = 2.5

    const val MAP_DEFAULT_LAT = 28.0

    const val MAP_DEFAULT_ZOOM = 5.0

    const val MAP_DEFAULT_BEARING = 0.0

    const val MAP_DEFAULT_PITCH = 0.0

    const val GEOMAN_SNAP_THRESHOLD_PX = 20

    const val MIN_ROAD_LENGTH_METERS = 10
}
