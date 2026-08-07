package com.nars.maplibre.modes

/**
 * Central place for building MapLibre source/layer names from feature ids.
 *
 * Feature ids are client-side UUIDs containing hyphens, so every name must be
 * sanitized identically — otherwise code that adds layers and code that removes
 * them compute different names and removal silently no-ops.
 */
internal object FeatureLayerNames {
    private const val SOURCE_PREFIX = "nars"
    private const val LAYER_PREFIX = "nars_layer"
    private val SAFE_ID_REGEX = Regex("[^a-zA-Z0-9_]")

    fun safeId(id: String): String = id.replace(SAFE_ID_REGEX, "_")

    fun sourceName(id: String): String = "${SOURCE_PREFIX}_${safeId(id)}"

    fun layerName(id: String): String = "${LAYER_PREFIX}_${safeId(id)}"
}
