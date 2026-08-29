package com.nars.maplibre.modes

import android.content.Context
import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.options.GmOptionsData
import com.geoman.maplibre.geoman.core.options.SettingsOptions
import com.geoman.maplibre.geoman.types.DrawModeName
import com.geoman.maplibre.geoman.types.EditModeName
import com.geoman.maplibre.geoman.types.ModeType
import com.nars.maplibre.data.model.DrawType
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.PhaseDefinition
import com.nars.maplibre.utils.Config
import com.nars.maplibre.utils.NarsLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

class NarsGeoman internal constructor(
    val geoman: Geoman,
    val displayManager: FeatureDisplayManager,
    val snappingEngine: SnappingEngine,
    private val eventHandler: GeomanEventHandler,
    private val geometryConverter: GeometryConverter,
    private val callbacks: FeatureCallbacks,
    private val scope: CoroutineScope,
) {
    @Volatile
    private var destroyed = false

    @Volatile
    private var currentPhase: PhaseDefinition? = null

    private val _isDrawing = MutableStateFlow(false)
    val isDrawing: StateFlow<Boolean> = _isDrawing.asStateFlow()

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    companion object {
        /**
         * Factory for creating a fully-configured [NarsGeoman] instance.
         * Initializes the Geoman engine, feature renderer, display manager, and event handlers.
         */
        operator fun invoke(
            mapView: MapView,
            map: MapLibreMap,
            context: Context,
            scope: CoroutineScope,
            onFeatureCreated: (NarsFeature) -> Unit,
            onFeatureUpdated: (NarsFeature) -> Unit,
            onFeatureDeleted: (String) -> Unit,
        ): NarsGeoman {
            val options =
                GmOptionsData(
                    settings =
                    SettingsOptions(
                        useControlsUi = true,
                        showControlsOnMap = false,
                        enableSnap = true,
                        snapDistance = Config.GEOMAN_SNAP_THRESHOLD_PX.toFloat(),
                    ),
                )
            val geoman = Geoman(mapView, map, options)
            val labelAndMarkerManager = LabelAndMarkerManager(map)
            val featureRenderer = FeatureRenderer(map, labelAndMarkerManager)
            val geometryConverter = GeometryConverter()
            val displayManager = FeatureDisplayManager(geoman, featureRenderer, geometryConverter, map)
            // NarsGeoman owns an independent scope (sharing the caller's
            // dispatcher) for the event collector. destroy() cancels only this
            // scope — never the shared screen scope that network calls run on.
            val internalScope = CoroutineScope(scope.coroutineContext + SupervisorJob())
            val eventHandler =
                GeomanEventHandler(
                    internalScope,
                    geoman,
                    onFeatureCreated,
                    onFeatureUpdated,
                    onFeatureDeleted,
                )
            eventHandler.setupEventListeners()
            NarsLogger.d(
                "NarsGeoman",
                "Initialized — delegating display to FeatureDisplayManager",
            )
            return NarsGeoman(
                geoman = geoman,
                displayManager = displayManager,
                eventHandler = eventHandler,
                geometryConverter = geometryConverter,
                snappingEngine = SnappingEngine(),
                callbacks = FeatureCallbacks(onFeatureCreated, onFeatureUpdated, onFeatureDeleted),
                scope = internalScope,
            )
        }
    }

    fun setCurrentPhase(phase: PhaseDefinition) {
        currentPhase = phase
        displayManager.currentPhase = phase
        eventHandler.setCurrentPhase(phase)
    }

    fun startDrawing() {
        val phase = currentPhase ?: return
        NarsLogger.d("NarsGeoman", "startDrawing for phase: ${phase.label}, drawType: ${phase.drawType}")

        geoman.disableAllModes()
        _isEditing.value = false
        eventHandler.setEditingFeature(null, null)
        _isDrawing.value = true

        when (phase.drawType) {
            DrawType.POLYGON -> geoman.enableMode(ModeType.DRAW, DrawModeName.POLYGON.name)
            DrawType.POLYLINE -> geoman.enableMode(ModeType.DRAW, DrawModeName.LINE.name)
            DrawType.CIRCLE -> geoman.enableMode(ModeType.DRAW, DrawModeName.CIRCLE.name)
            DrawType.MARKER -> geoman.enableMode(ModeType.DRAW, DrawModeName.MARKER.name)
        }
    }

    fun stopDrawing() {
        _isDrawing.value = false
        geoman.disableAllModes()
    }

    fun startEditing(feature: NarsFeature) {
        NarsLogger.d("NarsGeoman", "startEditing: ${feature.id}, type=${feature.type}")
        geoman.disableAllModes()
        _isDrawing.value = false

        eventHandler.setEditingFeature(feature.id, feature)
        _isEditing.value = true

        val geoJsonFeature = geometryConverter.convertToGeoJson(feature)
        geoman.addGeoJsonFeature(
            geoJsonFeature,
            geometryConverter.getSourceNameForGeometry(feature.geometry),
        )

        geoman.enableMode(ModeType.EDIT, EditModeName.CHANGE.name)
        geoman.startEditingFeature(geometryConverter.convertToGeomanFeatureData(feature))
    }

    fun stopEditing() {
        _isEditing.value = false
        eventHandler.setEditingFeature(null, null)
        geoman.disableAllModes()
    }

    fun commitEdits(notify: Boolean = true): NarsFeature? {
        val originalFeature = eventHandler.getEditingFeature() ?: return null

        var updatedGeometry: com.nars.maplibre.data.model.Geometry? = null
        for (sourceName in GEOMAN_SOURCE_NAMES) {
            val featureData = geoman.features.getFeature(sourceName, originalFeature.id)
            if (featureData != null) {
                updatedGeometry = eventHandler.extractGeometryFromFeatureData(featureData)
                break
            }
        }

        val updated =
            if (updatedGeometry != null) {
                originalFeature.copy(geometry = updatedGeometry)
            } else {
                NarsLogger.w("NarsGeoman", "No updated geometry for ${originalFeature.id}, skipping commit")
                stopEditing()
                return null
            }
        if (notify) callbacks.onUpdated(updated)
        stopEditing()
        return updated
    }

    fun cancelEdits() {
        val featureId = eventHandler.getEditingFeatureId() ?: return
        for (sourceName in GEOMAN_SOURCE_NAMES) {
            val featureData = geoman.features.getFeature(sourceName, featureId)
            if (featureData != null) {
                geoman.features.removeFeature(sourceName, featureId)
                break
            }
        }
        stopEditing()
    }

    fun onMapClick(latLng: LatLng) {
        if (_isDrawing.value) {
            enabledModeName(ModeType.DRAW)?.let { modeName ->
                geoman.handleDrawClick(modeName, latLng)
            }
        } else if (_isEditing.value) {
            enabledModeName(ModeType.EDIT)?.let { modeName ->
                geoman.handleEditClick(modeName, latLng)
            }
        }
    }

    fun onMapLongClick(latLng: LatLng) {
        if (_isDrawing.value) {
            enabledModeName(ModeType.DRAW)?.let { modeName ->
                geoman.handleDrawLongPress(modeName, latLng)
            }
        }
    }

    private fun enabledModeName(type: ModeType): String? =
        geoman.getEnabledModes().firstOrNull { it.first == type }?.second

    private val destroyLock = Any()

    fun destroy() {
        synchronized(destroyLock) {
            if (destroyed) return
            destroyed = true
        }
        eventHandler.destroy()
        stopDrawing()
        stopEditing()
        geoman.destroy()
        scope.cancel()
    }
}

/**
 * Callbacks for feature lifecycle events from [NarsGeoman].
 */
class FeatureCallbacks(
    val onCreated: (NarsFeature) -> Unit,
    val onUpdated: (NarsFeature) -> Unit,
    val onDeleted: (String) -> Unit,
)
