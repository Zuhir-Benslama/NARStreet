package com.nars.maplibre.ui.components

import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nars.maplibre.MapViewModel
import com.nars.maplibre.data.model.BaseLayerType
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.utils.Config
import com.nars.maplibre.utils.NarsLogger
import com.nars.maplibre.utils.TileSources
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * MapLibre Map Composable for NARS
 * Integrates MapLibre Android SDK with Jetpack Compose
 */
@Composable
fun NarsMap(viewModel: MapViewModel, callbacks: NarsMapCallbacks, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val baseLayer by viewModel.baseLayer.collectAsState()

    var currentOnMapClick by remember { mutableStateOf(callbacks.onMapClick) }
    var currentOnMapLongClick by remember { mutableStateOf(callbacks.onMapLongClick) }
    var currentShouldHandleClick by remember { mutableStateOf(callbacks.shouldHandleClick) }

    LaunchedEffect(callbacks.onMapClick) { currentOnMapClick = callbacks.onMapClick }
    LaunchedEffect(callbacks.onMapLongClick) { currentOnMapLongClick = callbacks.onMapLongClick }
    LaunchedEffect(callbacks.shouldHandleClick) { currentShouldHandleClick = callbacks.shouldHandleClick }

    val mapViewBundle = rememberSaveable { Bundle() }
    val mapView = remember { MapView(context) }

    MapViewLifecycleEffect(lifecycleOwner, mapView, mapViewBundle)
    BaseLayerSyncEffect(mapView, baseLayer, callbacks.onStyleLoaded)

    AndroidView(
        factory = { ctx ->
            mapView.apply {
                getMapAsync { mapLibreMap ->
                    configureMap(
                        map = mapLibreMap,
                        onMapClick = { latLng -> currentOnMapClick?.invoke(latLng) },
                        onMapLongClick = { latLng -> currentOnMapLongClick?.invoke(latLng) },
                        shouldHandleClick = { currentShouldHandleClick?.invoke() ?: true },
                    )

                    val cameraPosition = CameraPosition.Builder()
                        .target(LatLng(Config.MAP_DEFAULT_LAT, Config.MAP_DEFAULT_LNG))
                        .zoom(Config.MAP_DEFAULT_ZOOM)
                        .bearing(Config.MAP_DEFAULT_BEARING)
                        .tilt(Config.MAP_DEFAULT_PITCH)
                        .build()
                    mapLibreMap.setCameraPosition(cameraPosition)

                    initializeBaseLayer(mapLibreMap, baseLayer)

                    callbacks.onMapReady(this, mapLibreMap)
                }
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * Drives the MapView's Android lifecycle from the Compose lifecycle owner so
 * the map is released/recreated in step with the host.
 */
@Composable
private fun MapViewLifecycleEffect(lifecycleOwner: LifecycleOwner, mapView: MapView, mapViewBundle: Bundle) {
    DisposableEffect(lifecycleOwner, mapView) {
        var destroyed = false
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_CREATE -> {
                        mapView.onCreate(mapViewBundle)
                    }

                    Lifecycle.Event.ON_START -> {
                        mapView.onStart()
                    }

                    Lifecycle.Event.ON_RESUME -> {
                        mapView.onResume()
                    }

                    Lifecycle.Event.ON_PAUSE -> {
                        mapView.onPause()
                    }

                    Lifecycle.Event.ON_STOP -> {
                        mapView.onStop()
                    }

                    Lifecycle.Event.ON_DESTROY -> {
                        if (!destroyed) {
                            destroyed = true
                            mapView.onSaveInstanceState(mapViewBundle)
                            mapView.onDestroy()
                        }
                    }

                    else -> {
                        // Other lifecycle events need no map handling
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (!destroyed) {
                destroyed = true
                mapView.onSaveInstanceState(mapViewBundle)
                mapView.onStop()
                mapView.onPause()
                mapView.onDestroy()
            }
        }
    }
}

/**
 * Reacts to base-layer changes. The initial layer is applied once in
 * getMapAsync, so this effect only handles subsequent changes (avoids a
 * duplicate style load at startup).
 */
@Composable
private fun BaseLayerSyncEffect(mapView: MapView, baseLayer: BaseLayerType, onStyleLoaded: (() -> Unit)?) {
    var appliedLayer by remember { mutableStateOf(baseLayer) }
    LaunchedEffect(baseLayer) {
        if (appliedLayer == baseLayer) return@LaunchedEffect
        appliedLayer = baseLayer
        updateBaseLayer(mapView, baseLayer, onStyleLoaded)
    }
}

/**
 * Configure MapLibre map
 */
private fun configureMap(
    map: MapLibreMap,
    onMapClick: ((LatLng) -> Unit)?,
    onMapLongClick: ((LatLng) -> Unit)?,
    shouldHandleClick: (() -> Boolean)? = null,
) {
    // Set compass position
    map.uiSettings.compassGravity = android.view.Gravity.TOP or android.view.Gravity.END

    // Set attribution position
    map.uiSettings.attributionGravity = android.view.Gravity.BOTTOM or android.view.Gravity.START

    // Set logo position
    map.uiSettings.logoGravity = android.view.Gravity.BOTTOM or android.view.Gravity.START

    // Add map click listener
    onMapClick?.let { clickHandler ->
        map.addOnMapClickListener { latLng ->
            // Check if we should handle this click (for drawing/editing mode detection)
            if (shouldHandleClick?.invoke() == false) {
                // Skip click handling when in drawing/editing mode
                NarsLogger.d("NarsMap", "Skipping feature selection click - in drawing/editing mode")
                return@addOnMapClickListener false // Let Geoman handle it
            }
            NarsLogger.d("NarsMap", "Processing feature selection click")
            clickHandler(latLng)
            false // Return false to allow Geoman's listener to also process
        }
    }

    // Add map long click listener — always forward long clicks (needed for
    // finishing shapes in drawing mode). Feature-selection long clicks are
    // handled upstream in MapScreen.
    onMapLongClick?.let { longClickHandler ->
        map.addOnMapLongClickListener { latLng ->
            longClickHandler(latLng)
            false // Return false to allow Geoman's listener to also process
        }
    }
}

/**
 * Initialize base layer on map ready
 */
private fun initializeBaseLayer(map: MapLibreMap, initialLayer: BaseLayerType) {
    map.setStyle(Style.Builder().fromJson(getStyleJson(initialLayer)))
}

/**
 * Update base layer style
 */
private fun updateBaseLayer(mapView: MapView, layer: BaseLayerType, onStyleLoaded: (() -> Unit)? = null) {
    mapView.getMapAsync { map ->
        map.setStyle(Style.Builder().fromJson(getStyleJson(layer))) {
            // Style swap destroys all sources/layers added to the previous style;
            // notify the caller so features can be re-rendered on the new style.
            onStyleLoaded?.invoke()
        }
    }
}

private data class LayerConfig(val sourceId: String, val layerId: String, val tiles: String, val attribution: String)

/**
 * Get style JSON for layer type
 * Uses Mapbox Style Specification built via kotlinx.serialization JSON API
 */
private fun getStyleJson(layer: BaseLayerType): String {
    val cfg = when (layer) {
        BaseLayerType.SATELLITE -> LayerConfig(
            "esri-satellite",
            "satellite-layer",
            TileSources.SATELLITE,
            TileSources.ATTR_ESRI,
        )

        BaseLayerType.STREET -> LayerConfig(
            "osm-tiles",
            "osm-layer",
            TileSources.STREET,
            TileSources.ATTR_OSM,
        )

        BaseLayerType.LIGHT -> LayerConfig(
            "carto-light",
            "carto-light-layer",
            TileSources.LIGHT,
            TileSources.ATTR_CARTO,
        )

        BaseLayerType.DARK -> LayerConfig(
            "carto-dark",
            "carto-dark-layer",
            TileSources.DARK,
            TileSources.ATTR_CARTO,
        )
    }
    return buildJsonObject {
        put("version", TileSources.STYLE_VERSION)
        putJsonObject("sources") {
            putJsonObject(cfg.sourceId) {
                put("type", "raster")
                putJsonArray("tiles") { add(cfg.tiles) }
                put("tileSize", TileSources.TILE_SIZE)
                put("attribution", cfg.attribution)
            }
        }
        put("glyphs", TileSources.GLYPHS)
        putJsonArray("layers") {
            addJsonObject {
                put("id", cfg.layerId)
                put("type", "raster")
                put("source", cfg.sourceId)
                put("minzoom", 0)
                put("maxzoom", TileSources.MAP_MAX_ZOOM)
            }
        }
    }.toString()
}
