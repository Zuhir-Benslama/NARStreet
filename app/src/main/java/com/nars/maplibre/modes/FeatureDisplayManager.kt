package com.nars.maplibre.modes

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.nars.maplibre.data.model.CircleGeometry
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.PhaseDefinition
import com.nars.maplibre.data.model.Phases
import com.nars.maplibre.data.model.PolygonGeometry
import com.nars.maplibre.utils.NarsLogger
import org.maplibre.android.maps.MapLibreMap
import java.util.Collections

val GEOMAN_SOURCE_NAMES =
    listOf(
        GeomanCoreConstants.SOURCE_MARKERS,
        GeomanCoreConstants.SOURCE_LINES,
        GeomanCoreConstants.SOURCE_POLYGONS,
        GeomanCoreConstants.SOURCE_CIRCLES,
    )

class FeatureDisplayManager(
    private val geoman: Geoman,
    private val featureRenderer: FeatureRenderer,
    private val geometryConverter: GeometryConverter,
    private val map: MapLibreMap?,
) {
    private val displayedFeatureIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    @Volatile
    var currentPhase: PhaseDefinition? = null

    private var lastRoadEndpointSignature: String? = null

    fun addFeature(feature: NarsFeature) {
        displayedFeatureIds.add(feature.id)
        featureRenderer.addFeature(feature)
        val geoJsonFeature = geometryConverter.convertToGeoJson(feature)
        geoman.addGeoJsonFeature(geoJsonFeature, geometryConverter.getSourceNameForGeometry(feature.geometry))
    }

    fun addFeatures(features: List<NarsFeature>) {
        val currentPhaseKey = currentPhase?.key
        val filtered =
            if (currentPhaseKey != null) {
                features.filter { it.properties.phase == currentPhaseKey }
            } else {
                features
            }
        filtered.forEach { addFeature(it) }
    }

    fun updateDisplayedFeatures(allFeatures: List<NarsFeature>) {
        val currentPhaseKey = currentPhase?.key
        val filtered =
            if (currentPhaseKey != null) {
                allFeatures.filter { it.properties.phase == currentPhaseKey }
            } else {
                allFeatures
            }
        val newIds = filtered.map { it.id }.toSet()

        val toRemove = displayedFeatureIds - newIds
        toRemove.forEach { removeFeature(it) }

        val toAdd = filtered.filter { it.id !in displayedFeatureIds }
        toAdd.forEach { addFeature(it) }

        displayedFeatureIds.retainAll(newIds)
        displayedFeatureIds.addAll(newIds.filterNot { it in displayedFeatureIds })

        if (currentPhaseKey == Phases.ROADS_KEY) {
            val signature = roadEndpointSignature(allFeatures)
            if (signature != lastRoadEndpointSignature) {
                featureRenderer.labelAndMarkerManager.addRoadEndpointMarkers(allFeatures)
                lastRoadEndpointSignature = signature
            }
        }
    }

    private fun roadEndpointSignature(allFeatures: List<NarsFeature>): String = allFeatures
        .filter { it.properties.phase == Phases.ROADS_KEY }
        .joinToString("|") { road -> "${road.id}:${road.geometry}:${road.properties.name}" }

    fun updateFeatureOnMap(feature: NarsFeature) {
        val sourceName = FeatureLayerNames.sourceName(feature.id)
        val source = map?.style?.getSource(sourceName)
        if (source is org.maplibre.android.style.sources.GeoJsonSource) {
            val geoJsonString = buildUpdatedGeoJson(feature)
            source.setGeoJson(geoJsonString)
            NarsLogger.d("FeatureDisplayManager", "Updated feature ${feature.id} in-place")
        } else {
            removeFeature(feature.id)
            addFeature(feature)
        }
    }

    private fun buildUpdatedGeoJson(feature: NarsFeature): String = when (val geom = feature.geometry) {
        // The circle is stored as a Point for Geoman but must render as a
        // polygon ring — rebuild it so the circle does not vanish on update.
        is CircleGeometry ->
            geometryConverter.buildCircleGeoJson(
                centerLng = geom.coordinates.getOrNull(0) ?: 0.0,
                centerLat = geom.coordinates.getOrNull(1) ?: 0.0,
                radiusMeters =
                geom.coordinates.getOrNull(2)?.takeIf { it > 0 }
                    ?: FeatureRenderer.DEFAULT_CIRCLE_RADIUS_METERS,
            )

        is PolygonGeometry -> {
            updatePolygonEdgesSource(feature, geom)
            geometryConverter.buildFeatureGeoJson(geometryConverter.convertToGeoJson(feature))
        }

        else -> geometryConverter.buildFeatureGeoJson(geometryConverter.convertToGeoJson(feature))
    }

    private fun updatePolygonEdgesSource(feature: NarsFeature, geom: PolygonGeometry) {
        val edgeSourceName = "${FeatureLayerNames.sourceName(feature.id)}_edges"
        val edgeSource = map?.style?.getSource(edgeSourceName)
        if (edgeSource is org.maplibre.android.style.sources.GeoJsonSource) {
            edgeSource.setGeoJson(geometryConverter.buildPolygonEdgesGeoJson(geom.coordinates))
        }
    }

    /**
     * Re-renders every feature after the map style has been replaced.
     * A base-layer switch replaces the style, which destroys all sources and
     * layers that were added to the previous style, so tracking sets are reset
     * and everything is re-added to the new style.
     */
    fun onStyleReloaded(allFeatures: List<NarsFeature>) {
        displayedFeatureIds.clear()
        featureRenderer.clearTracking()
        lastRoadEndpointSignature = null
        geoman.onStyleReloaded()
        updateDisplayedFeatures(allFeatures)
    }

    fun removeFeature(featureId: String) {
        displayedFeatureIds.remove(featureId)
        for (sourceName in GEOMAN_SOURCE_NAMES) {
            val featureData = geoman.features.getFeature(sourceName, featureId)
            if (featureData != null) {
                geoman.features.removeFeature(sourceName, featureId)
                break
            }
        }

        val layerName = FeatureLayerNames.layerName(featureId)
        val layerNames =
            listOf(
                layerName,
                "${layerName}_outline",
                "${layerName}_stroke",
                "${layerName}_label",
            )
        for (name in layerNames) {
            try {
                map?.style?.getLayer(name)?.let { map.style?.removeLayer(it) }
            } catch (e: IllegalArgumentException) {
                NarsLogger.w("FeatureDisplayManager", "Failed to remove layer $name: ${e.message}")
            } catch (e: IllegalStateException) {
                NarsLogger.w("FeatureDisplayManager", "Failed to remove layer $name: ${e.message}")
            }
        }
        val mapSourceNames =
            listOf(
                "${FeatureLayerNames.sourceName(featureId)}_edges",
                FeatureLayerNames.sourceName(featureId),
            )
        for (name in mapSourceNames) {
            try {
                map?.style?.removeSource(name)
            } catch (e: IllegalArgumentException) {
                NarsLogger.w("FeatureDisplayManager", "Failed to remove source $name: ${e.message}")
            } catch (e: IllegalStateException) {
                NarsLogger.w("FeatureDisplayManager", "Failed to remove source $name: ${e.message}")
            }
        }

        featureRenderer.labelAndMarkerManager.removeVertexMarkers(featureId)
        featureRenderer.removeFromTracking(featureId)
        NarsLogger.d("FeatureDisplayManager", "Removed feature $featureId")
    }

    fun clearAllFeatures() {
        displayedFeatureIds.clear()
        lastRoadEndpointSignature = null
        geoman.clearAllFeatures()
        featureRenderer.clearTracking()
    }
}
