package com.nars.maplibre.modes

import android.graphics.Color
import androidx.core.graphics.toColorInt
import com.nars.maplibre.data.model.LineStringGeometry
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.Phases
import com.nars.maplibre.data.model.PolygonGeometry
import com.nars.maplibre.utils.NarsLogger
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

class LabelAndMarkerManager(private val map: MapLibreMap) {
    companion object {
        private const val TAG = "LabelAndMarkerManager"
        private const val LABEL_TEXT_SIZE = 14f
        private const val LABEL_HALO_WIDTH = 2f
        private const val ROAD_LABEL_HALO_WIDTH = 3f
        private const val VERTEX_CIRCLE_RADIUS = 6f
        private const val ROAD_MARKER_RADIUS = 14f
        private const val ROAD_MARKER_STROKE_WIDTH = 3f
    }

    fun addLabelLayer(layerName: String, sourceName: String, labelText: String?) {
        if (labelText.isNullOrBlank()) return
        val labelLayerName = "${layerName}_label"
        try {
            map.style?.getLayer(labelLayerName)?.let { map.style?.removeLayer(labelLayerName) }
        } catch (e: IllegalArgumentException) {
            NarsLogger.w(TAG, "Failed to remove existing label layer", e)
        }

        val labelLayer =
            SymbolLayer(labelLayerName, sourceName).apply {
                setProperties(
                    org.maplibre.android.style.layers.PropertyFactory
                        .textField(Expression.get("name")),
                    org.maplibre.android.style.layers.PropertyFactory
                        .textColor(Color.BLACK),
                    org.maplibre.android.style.layers.PropertyFactory
                        .textSize(LABEL_TEXT_SIZE),
                    org.maplibre.android.style.layers.PropertyFactory
                        .textFont(arrayOf("Noto Sans Regular")),
                    org.maplibre.android.style.layers.PropertyFactory
                        .textAllowOverlap(true),
                    org.maplibre.android.style.layers.PropertyFactory
                        .textIgnorePlacement(true),
                    org.maplibre.android.style.layers.PropertyFactory
                        .textHaloColor(Color.WHITE),
                    org.maplibre.android.style.layers.PropertyFactory
                        .textHaloWidth(LABEL_HALO_WIDTH),
                )
            }
        try {
            map.style?.addLayer(labelLayer)
        } catch (e: IllegalArgumentException) {
            NarsLogger.w(TAG, "Error adding label", e)
        }
    }

    private val roadMarkerRoadIds: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    private fun removeRoadEndpointMarkers() {
        for (roadId in roadMarkerRoadIds) {
            val parts = listOf("${roadId}_start", "${roadId}_end", "${roadId}_label")
            for (part in parts) {
                try {
                    map.style?.getLayer("nars_${part}_circle")?.let { map.style?.removeLayer(it) }
                    map.style?.getLayer("nars_$part")?.let { map.style?.removeLayer(it) }
                    map.style?.getSource("nars_${part}_src")?.let { map.style?.removeSource(it) }
                } catch (e: IllegalArgumentException) {
                    NarsLogger.w(TAG, "Error removing road endpoint marker layer '$part'", e)
                }
            }
        }
        roadMarkerRoadIds.clear()
    }

    fun addRoadEndpointMarkers(allFeatures: List<NarsFeature>) {
        removeRoadEndpointMarkers()
        allFeatures
            .filter { it.properties.phase == Phases.ROADS_KEY }
            .mapNotNull { road -> (road.geometry as? LineStringGeometry)?.let { road to it } }
            .mapNotNull { (road, geometry) ->
                val coords = geometry.coordinates.chunked(2).filter { it.size == 2 }
                if (coords.size >= 2) road to coords else null
            }
            .forEach { (road, coords) ->
                val safeRoadId = FeatureLayerNames.safeId(road.id)
                addEndpointMarker(
                    id = "${safeRoadId}_start",
                    lon = coords.first()[0],
                    lat = coords.first()[1],
                    isStart = true,
                )
                addEndpointMarker(
                    id = "${safeRoadId}_end",
                    lon = coords.last()[0],
                    lat = coords.last()[1],
                    isStart = false,
                )
                val midIdx = coords.size / 2
                addLabelAt(
                    layerName = "nars_${safeRoadId}_label",
                    labelText = road.properties.name,
                    lon = coords[midIdx][0],
                    lat = coords[midIdx][1],
                )
                roadMarkerRoadIds.add(safeRoadId)
            }
    }

    private fun pointFeatureGeoJson(lon: Double, lat: Double, props: Map<String, String> = emptyMap()): String =
        buildJsonObject {
            put("type", "FeatureCollection")
            putJsonArray("features") {
                addJsonObject {
                    put("type", "Feature")
                    putJsonObject("geometry") {
                        put("type", "Point")
                        putJsonArray("coordinates") {
                            add(lon)
                            add(lat)
                        }
                    }
                    putJsonObject("properties") {
                        props.forEach { (k, v) -> put(k, v) }
                    }
                }
            }
        }.toString()

    private fun addEndpointMarker(id: String, lon: Double, lat: Double, isStart: Boolean) {
        val layerName = "nars_$id"
        val sourceName = "${layerName}_src"
        val geoJson = pointFeatureGeoJson(lon, lat)

        try {
            map.style?.addSource(GeoJsonSource(sourceName, geoJson))
        } catch (e: IllegalArgumentException) {
            NarsLogger.w(TAG, "Failed to add endpoint marker source", e)
            return
        }

        val color = if (isStart) "#2ecc71".toColorInt() else "#e74c3c".toColorInt()
        val circleLayer =
            CircleLayer("${layerName}_circle", sourceName).apply {
                setProperties(
                    org.maplibre.android.style.layers.PropertyFactory
                        .circleColor(color),
                    org.maplibre.android.style.layers.PropertyFactory
                        .circleRadius(ROAD_MARKER_RADIUS),
                    org.maplibre.android.style.layers.PropertyFactory
                        .circleStrokeColor(Color.WHITE),
                    org.maplibre.android.style.layers.PropertyFactory
                        .circleStrokeWidth(ROAD_MARKER_STROKE_WIDTH),
                )
            }
        try {
            map.style?.addLayer(circleLayer)
        } catch (e: IllegalArgumentException) {
            NarsLogger.w(TAG, "Failed to add circle layer", e)
        }
    }

    private fun addLabelAt(layerName: String, labelText: String?, lon: Double, lat: Double) {
        if (labelText.isNullOrBlank()) return
        val sourceName = "${layerName}_src"
        val geoJson = pointFeatureGeoJson(lon, lat, mapOf("text" to labelText))

        try {
            map.style?.addSource(GeoJsonSource(sourceName, geoJson))
        } catch (e: IllegalArgumentException) {
            NarsLogger.w(TAG, "Failed to add label source", e)
        }

        val symbolLayer =
            SymbolLayer(layerName, sourceName).apply {
                setProperties(
                    org.maplibre.android.style.layers.PropertyFactory
                        .textField(Expression.get("text")),
                    org.maplibre.android.style.layers.PropertyFactory
                        .textColor(Color.BLACK),
                    org.maplibre.android.style.layers.PropertyFactory
                        .textSize(LABEL_TEXT_SIZE),
                    org.maplibre.android.style.layers.PropertyFactory
                        .textFont(arrayOf("Noto Sans Regular")),
                    org.maplibre.android.style.layers.PropertyFactory
                        .textAllowOverlap(true),
                    org.maplibre.android.style.layers.PropertyFactory
                        .textHaloColor(Color.WHITE),
                    org.maplibre.android.style.layers.PropertyFactory
                        .textHaloWidth(ROAD_LABEL_HALO_WIDTH),
                )
            }
        try {
            map.style?.addLayer(symbolLayer)
        } catch (e: IllegalArgumentException) {
            NarsLogger.w(TAG, "Failed to add label layer", e)
        }
    }

    fun addVertexMarkers(feature: NarsFeature) {
        removeVertexMarkers(feature.id)

        val coordinates = vertexCoordinates(feature) ?: return

        val sourceName = "nars_vertices_${feature.id}"
        val layerName = "nars_vertex_layer_${feature.id}"

        try {
            map.style?.getSource(sourceName)?.let { map.style?.removeSource(sourceName) }
            map.style?.addSource(GeoJsonSource(sourceName, vertexGeoJson(coordinates)))
        } catch (e: IllegalArgumentException) {
            NarsLogger.w(TAG, "Error adding vertex source", e)
            return
        }

        try {
            map.style?.addLayer(buildVertexCircleLayer(layerName, sourceName))
        } catch (e: IllegalArgumentException) {
            NarsLogger.w(TAG, "Error adding vertex layer", e)
        }
    }

    private fun vertexCoordinates(feature: NarsFeature): List<DoubleArray>? = when (feature.geometry) {
        is LineStringGeometry -> feature.geometry.coordinates
        is PolygonGeometry -> feature.geometry.coordinates
        else -> null
    }?.chunked(2)
        ?.filter { it.size == 2 }
        ?.map { doubleArrayOf(it[0], it[1]) }

    private fun vertexGeoJson(coordinates: List<DoubleArray>): String = buildJsonObject {
        put("type", "FeatureCollection")
        putJsonArray("features") {
            coordinates.forEach { coord ->
                addJsonObject {
                    put("type", "Feature")
                    putJsonObject("geometry") {
                        put("type", "Point")
                        putJsonArray("coordinates") {
                            add(coord[0])
                            add(coord[1])
                        }
                    }
                    putJsonObject("properties") { put("isVertex", true) }
                }
            }
        }
    }.toString()

    private fun buildVertexCircleLayer(layerName: String, sourceName: String): CircleLayer =
        CircleLayer(layerName, sourceName).apply {
            setProperties(
                org.maplibre.android.style.layers.PropertyFactory
                    .circleColor(Color.RED),
                org.maplibre.android.style.layers.PropertyFactory
                    .circleRadius(VERTEX_CIRCLE_RADIUS),
                org.maplibre.android.style.layers.PropertyFactory
                    .circleStrokeColor(Color.WHITE),
                org.maplibre.android.style.layers.PropertyFactory
                    .circleStrokeWidth(2f),
            )
        }

    fun removeVertexMarkers(featureId: String) {
        val layerName = "nars_vertex_layer_$featureId"
        val sourceName = "nars_vertices_$featureId"
        try {
            map.style?.getLayer(layerName)?.let {
                map.style?.removeLayer(it)
            }
            map.style?.getSource(sourceName)?.let {
                map.style?.removeSource(sourceName)
            }
        } catch (e: IllegalArgumentException) {
            NarsLogger.w(TAG, "Failed to remove vertex markers for $featureId", e)
        }
    }
}
