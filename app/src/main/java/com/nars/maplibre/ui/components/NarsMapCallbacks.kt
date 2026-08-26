package com.nars.maplibre.ui.components

import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

data class NarsMapCallbacks(
    val onMapReady: (MapView, MapLibreMap) -> Unit,
    val onMapClick: ((LatLng) -> Unit)? = null,
    val onMapLongClick: ((LatLng) -> Unit)? = null,
    val shouldHandleClick: (() -> Boolean)? = null,
    val onStyleLoaded: (() -> Unit)? = null,
)
