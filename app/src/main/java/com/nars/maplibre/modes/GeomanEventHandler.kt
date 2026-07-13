package com.nars.maplibre.modes

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.events.GmDrawEvent
import com.geoman.maplibre.geoman.types.events.GmEditEvent
import com.geoman.maplibre.geoman.types.events.GmMapEvent
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.Polygon
import com.geoman.maplibre.geoman.utils.GeometryUtils
import com.nars.maplibre.data.model.CircleGeometry
import com.nars.maplibre.data.model.FeatureProperties
import com.nars.maplibre.data.model.Geometry
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.NarsFeatureType
import com.nars.maplibre.data.model.PhaseDefinition
import com.nars.maplibre.data.model.Phases
import com.nars.maplibre.utils.NarsLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class GeomanEventHandler(
    private val scope: CoroutineScope,
    private val geoman: Geoman,
    private val onFeatureCreated: (NarsFeature) -> Unit,
    private val onFeatureUpdated: (NarsFeature) -> Unit,
    private val onFeatureDeleted: (String) -> Unit,
) {
    companion object {
        private const val TAG = "GeomanEventHandler"
    }

    @Volatile private var currentPhase: PhaseDefinition? = null
    @Volatile private var editingFeatureId: String? = null
    @Volatile private var editingFeature: NarsFeature? = null

    fun setCurrentPhase(phase: PhaseDefinition) {
        currentPhase = phase
    }

    fun setEditingFeature(id: String?, feature: NarsFeature?) {
        editingFeatureId = id
        editingFeature = feature
    }

    fun getEditingFeatureId(): String? = editingFeatureId

    fun getEditingFeature(): NarsFeature? = editingFeature

    fun setupEventListeners() {
        scope.launch {
            geoman.events.events.collect { event ->
                when (event) {
                    is GmMapEvent.Loaded -> {
                        NarsLogger.d(TAG, "Geoman loaded")
                    }

                    is GmDrawEvent.Create -> {
                        NarsLogger.d(TAG, "GmDrawEvent.Create received: shape=${event.shape}")
                        handleFeatureCreated(event.feature as? FeatureData)
                    }

                    is GmDrawEvent.EditEnd -> {
                        NarsLogger.d(TAG, "Edit ended: ${event.shape}")
                        handleEditEnd()
                    }

                    is GmEditEvent.ChangeEnd -> {
                        NarsLogger.d(TAG, "Geometry changed")
                        handleGeometryChanged(event.feature as? FeatureData)
                    }

                    is GmEditEvent.Delete -> {
                        NarsLogger.d(TAG, "Feature deleted")
                        handleDeleted()
                    }
                }
            }
        }
    }

    internal fun handleFeatureCreated(featureData: FeatureData?) {
        val phase =
            currentPhase ?: run {
                NarsLogger.e(TAG, "No current phase set when creating feature!")
                return
            }

        NarsLogger.d(TAG, "Creating feature for phase: ${phase.label} (${phase.key})")
        val narsFeature = createNarsFeatureFromFeatureData(featureData, phase) ?: run {
            NarsLogger.e(TAG, "Failed to extract geometry from feature data — discarding")
            return
        }
        onFeatureCreated(narsFeature)
    }

    internal fun createNarsFeatureFromFeatureData(featureData: FeatureData?, phase: PhaseDefinition): NarsFeature? {
        val geometry =
            if (featureData != null) {
                if (featureData.properties["shapeType"] == "circle" ||
                    featureData.properties["radius"] != null
                ) {
                    extractCircleGeometry(featureData)
                } else {
                    extractGeometryFromGeoJson(featureData.geometry)
                }
            } else {
                extractGeometryFromGeoJson(null)
            }

        val safeGeometry = geometry ?: return null

        return NarsFeature(
            id =
            featureData?.id ?: java.util.UUID
                .randomUUID()
                .toString(),
            type = getFeatureTypeFromPhase(phase),
            geometry = safeGeometry,
            properties =
            when (phase.key) {
                Phases.ROADS_KEY -> {
                    FeatureProperties.RoadProperties()
                }

                Phases.HOUSE_ENTRANCES_KEY -> {
                    FeatureProperties.HouseEntranceProperties()
                }

                Phases.NAMING_PANELS_KEY -> {
                    FeatureProperties.NamingPanelProperties()
                }

                else -> {
                    NarsLogger.e(TAG, "Unknown phase key: ${phase.key} — cannot create feature")
                    return null
                }
            },
        )
    }

    internal fun handleEditEnd() {
        editingFeatureId = null
        editingFeature = null
    }

    internal fun handleGeometryChanged(featureData: FeatureData?) {
        val data = featureData ?: return
        val geometry = extractGeometryFromFeatureData(data) ?: run {
            NarsLogger.e(TAG, "Failed to extract geometry during edit — skipping update")
            return
        }
        editingFeature?.let { original ->
            val updated = original.copy(geometry = geometry)
            onFeatureUpdated(updated)
        }
    }

    internal fun handleDeleted() {
        editingFeatureId?.let { featureId ->
            onFeatureDeleted(featureId)
        }
        editingFeatureId = null
        editingFeature = null
    }

    private fun extractGeometryFromGeoJson(geometry: com.geoman.maplibre.geoman.types.geojson.Geometry?): Geometry? =
        GeometryConverter.extractGeometryFromGeoJson(geometry)

    internal fun extractCircleGeometry(featureData: FeatureData): CircleGeometry? {
        val center = featureData.properties["center"] as? LngLat
        val radius = featureData.properties["radius"] as? Double

        if (center != null && radius != null) {
            return CircleGeometry(coordinates = listOf(center.longitude, center.latitude, radius))
        }

        val polygon = featureData.geometry as? Polygon
        if (polygon != null) {
            val ring = polygon.getExteriorRing()
            if (ring.size >= 2) {
                val sumLon = ring.sumOf { it.longitude }
                val sumLat = ring.sumOf { it.latitude }
                val avgLon = sumLon / ring.size
                val avgLat = sumLat / ring.size
                val centerPoint = LngLat(avgLon, avgLat)
                val centerLngLat = ring.first()
                val calcRadius =
                    GeometryUtils.calculateDistance(
                        centerPoint,
                        centerLngLat,
                    )
                return CircleGeometry(coordinates = listOf(avgLon, avgLat, calcRadius))
            }
        }

        NarsLogger.e(TAG, "Cannot extract circle geometry: no center/radius or valid polygon")
        return null
    }

    fun extractGeometryFromFeatureData(featureData: FeatureData): Geometry? =
        extractGeometryFromGeoJson(featureData.geometry)

    internal fun getFeatureTypeFromPhase(phase: PhaseDefinition): NarsFeatureType = when (phase.key) {
        Phases.ROADS_KEY -> NarsFeatureType.ROAD
        Phases.HOUSE_ENTRANCES_KEY -> NarsFeatureType.HOUSE_ENTRANCE
        Phases.NAMING_PANELS_KEY -> NarsFeatureType.NAMING_PANEL
        else -> NarsFeatureType.ROAD
    }
}
