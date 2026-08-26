package com.nars.maplibre.modes

import android.graphics.Color
import androidx.core.graphics.toColorInt
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.nars.maplibre.data.model.CircleGeometry
import com.nars.maplibre.data.model.LineStringGeometry
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.Phases
import com.nars.maplibre.data.model.PointGeometry
import com.nars.maplibre.data.model.PolygonGeometry
import com.nars.maplibre.utils.NarsLogger
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.Collections

class FeatureRenderer(internal val map: MapLibreMap, val labelAndMarkerManager: LabelAndMarkerManager) {

    internal var geoJsonSourceFactory: (name: String, json: String) -> GeoJsonSource =
        { name, json -> GeoJsonSource(name, json) }
    internal var lineLayerFactory: (name: String, source: String) -> LineLayer =
        { name, source -> LineLayer(name, source) }
    internal var fillLayerFactory: (name: String, source: String) -> FillLayer =
        { name, source -> FillLayer(name, source) }
    internal var symbolLayerFactory: (name: String, source: String) -> SymbolLayer =
        { name, source -> SymbolLayer(name, source) }
    internal var geometryConverterProvider: () -> GeometryConverter = { sharedGeometryConverter }

    companion object {
        private val sharedGeometryConverter = GeometryConverter()
        private const val TAG = "FeatureRenderer"
        private const val DEFAULT_FALLBACK_COLOR = "#8e44ad"
        private const val DEFAULT_MARKER_ICON_SIZE = 0.5f
        internal const val DEFAULT_CIRCLE_RADIUS_METERS = 50.0
        private const val CIRCLE_FILL_OPACITY = 0f

        const val STYLE_LINE_WIDTH_THIN = 2
        const val STYLE_LINE_WIDTH_THICK = 8
        const val STYLE_FILL_OPACITY_LIGHT = 0.20f
    }

    private val addedFeatureIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    fun addFeature(feature: NarsFeature) {
        if (addedFeatureIds.contains(feature.id)) {
            NarsLogger.w(TAG, "Feature ${feature.id} already exists, skipping")
            return
        }

        val geoJsonFeature = geometryConverterProvider().convertToGeoJson(feature)
        val sourceName = FeatureLayerNames.sourceName(feature.id)
        val layerName = FeatureLayerNames.layerName(feature.id)
        val geoJsonString = buildGeoJsonString(geoJsonFeature)

        removeExistingSource(sourceName)

        val style = map.style ?: run {
            NarsLogger.w(TAG, "Style not loaded — cannot add feature ${feature.id}")
            return
        }

        style.addSource(geoJsonSourceFactory(sourceName, geoJsonString))

        val featureStyle = getFeatureStyle(feature.properties.phase)
        when (feature.geometry) {
            is PointGeometry -> addPointLayer(style, layerName, sourceName)
            is LineStringGeometry -> addLineLayer(style, layerName, sourceName, featureStyle)
            is PolygonGeometry -> addPolygonLayer(style, layerName, sourceName, featureStyle, feature.geometry)
            is CircleGeometry -> addCircleLayer(style, layerName, sourceName, featureStyle, feature.geometry)
        }
        labelAndMarkerManager.addLabelLayer(layerName, sourceName, feature.properties.name)

        addedFeatureIds.add(feature.id)
    }

    private fun addPointLayer(style: Style, layerName: String, sourceName: String) {
        symbolLayerFactory(layerName, sourceName).apply {
            setProperties(
                PropertyFactory.iconImage("default-marker"),
                PropertyFactory.iconSize(DEFAULT_MARKER_ICON_SIZE),
                PropertyFactory.iconAllowOverlap(true),
            )
            style.addLayer(this)
        }
    }

    private fun addLineLayer(style: Style, layerName: String, sourceName: String, featureStyle: FeatureStyle) {
        lineLayerFactory(layerName, sourceName).apply {
            setProperties(
                PropertyFactory.lineColor(parseColor(featureStyle.lineColor)),
                PropertyFactory.lineWidth(featureStyle.lineWidth.toFloat()),
            )
            style.addLayer(this)
        }
    }

    private fun addPolygonLayer(
        style: Style,
        layerName: String,
        sourceName: String,
        featureStyle: FeatureStyle,
        geom: PolygonGeometry,
    ) {
        fillLayerFactory(layerName, sourceName).apply {
            setProperties(
                PropertyFactory.fillColor(parseColor(featureStyle.lineColor)),
                PropertyFactory.fillOpacity(STYLE_FILL_OPACITY_LIGHT),
            )
            style.addLayer(this)
        }

        val edgeSourceName = "${sourceName}_edges"
        removeExistingSource(edgeSourceName)
        val edgesJson = geometryConverterProvider().buildPolygonEdgesGeoJson(geom.coordinates)
        style.addSource(geoJsonSourceFactory(edgeSourceName, edgesJson))

        lineLayerFactory("${layerName}_outline", edgeSourceName).apply {
            setProperties(
                PropertyFactory.lineColor(parseColor(featureStyle.lineColor)),
                PropertyFactory.lineWidth(featureStyle.lineWidth.toFloat()),
            )
            style.addLayer(this)
        }
    }

    private fun addCircleLayer(
        style: Style,
        layerName: String,
        sourceName: String,
        featureStyle: FeatureStyle,
        geom: CircleGeometry,
    ) {
        val centerLng = geom.coordinates.getOrNull(0) ?: return
        val centerLat = geom.coordinates.getOrNull(1) ?: return
        val radiusMeters = geom.coordinates.getOrNull(2)?.takeIf { it > 0 }
        if (radiusMeters == null) {
            NarsLogger.w(
                TAG,
                "Circle ${geom.coordinates} has no positive radius — " +
                    "rendering with default ${DEFAULT_CIRCLE_RADIUS_METERS}m",
            )
        }
        val circleGeoJson =
            geometryConverterProvider()
                .buildCircleGeoJson(centerLng, centerLat, radiusMeters ?: DEFAULT_CIRCLE_RADIUS_METERS)

        removeExistingSource(sourceName)
        style.addSource(geoJsonSourceFactory(sourceName, circleGeoJson))

        fillLayerFactory(layerName, sourceName).apply {
            setProperties(
                PropertyFactory.fillOpacity(CIRCLE_FILL_OPACITY),
            )
            style.addLayer(this)
        }

        lineLayerFactory("${layerName}_stroke", sourceName).apply {
            setProperties(
                PropertyFactory.lineColor(parseColor(featureStyle.lineColor)),
                PropertyFactory.lineWidth(featureStyle.lineWidth.toFloat()),
            )
            style.addLayer(this)
        }
    }

    private fun removeExistingSource(sourceName: String) {
        try {
            map.style?.getSource(sourceName)?.let { map.style?.removeSource(sourceName) }
        } catch (e: IllegalArgumentException) {
            NarsLogger.w(TAG, "Error removing source $sourceName", e)
        } catch (e: IllegalStateException) {
            NarsLogger.w(TAG, "Error removing source $sourceName", e)
        }
    }

    private fun getFeatureStyle(phaseKey: String): FeatureStyle {
        val phase = Phases.getByKey(phaseKey)
        val color = phase?.color ?: DEFAULT_FALLBACK_COLOR
        val width = if (phaseKey == Phases.ROADS_KEY) STYLE_LINE_WIDTH_THICK else STYLE_LINE_WIDTH_THIN
        return FeatureStyle(color, width)
    }

    data class FeatureStyle(val lineColor: String, val lineWidth: Int)

    private fun parseColor(colorStr: String): Int = try {
        (if (colorStr.startsWith("#")) colorStr else "#$colorStr").toColorInt()
    } catch (e: IllegalArgumentException) {
        NarsLogger.w(TAG, "Failed to parse color: $colorStr", e)
        Color.GRAY
    }

    private fun buildGeoJsonString(feature: com.geoman.maplibre.geoman.types.geojson.Feature): String =
        geometryConverterProvider().buildFeatureGeoJson(feature)

    fun isFeatureAdded(featureId: String): Boolean = addedFeatureIds.contains(featureId)

    fun removeFromTracking(featureId: String) = addedFeatureIds.remove(featureId)

    fun clearTracking() = addedFeatureIds.clear()

    fun getTrackedCount(): Int = addedFeatureIds.size
}
