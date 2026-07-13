package com.nars.maplibre.modes

import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.types.geojson.Feature
import com.geoman.maplibre.geoman.types.geojson.Geometry
import com.geoman.maplibre.geoman.types.geojson.LineString
import com.geoman.maplibre.geoman.types.geojson.LngLat
import com.geoman.maplibre.geoman.types.geojson.Point
import com.geoman.maplibre.geoman.types.geojson.Polygon
import com.geoman.maplibre.geoman.types.geojson.MultiPolygon
import com.nars.maplibre.data.model.CircleGeometry
import com.nars.maplibre.data.model.LineStringGeometry
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.PointGeometry
import com.nars.maplibre.data.model.PolygonGeometry
import com.nars.maplibre.utils.GeometryUtils
import com.nars.maplibre.utils.NarsLogger
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Handles geometry conversions between NARS and GeoJSON formats
 */
class GeometryConverter {
    companion object {
        private const val TAG = "GeometryConverter"
        private const val CIRCLE_APPROXIMATION_SEGMENTS = 32
        private const val DEGREES_IN_CIRCLE = 360.0

        fun extractGeometryFromGeoJson(
            geometry: com.geoman.maplibre.geoman.types.geojson.Geometry?,
        ): com.nars.maplibre.data.model.Geometry? {
            if (geometry == null) return null
            return when (geometry) {
                is Point -> PointGeometry(coordinates = listOf(geometry.coordinates[0], geometry.coordinates[1]))
                is LineString -> LineStringGeometry(coordinates = geometry.coordinates.flatMap { listOf(it[0], it[1]) })
                is Polygon -> {
                    val ring = geometry.coordinates.firstOrNull() ?: return null
                    PolygonGeometry(coordinates = ring.flatMap { listOf(it[0], it[1]) })
                }
                is MultiPolygon -> {
                    val ring = geometry.coordinates.firstOrNull()?.firstOrNull() ?: return null
                    PolygonGeometry(coordinates = ring.flatMap { listOf(it[0], it[1]) })
                }
                else -> null
            }
        }
    }

    /**
     * Convert NarsFeature to GeoJSON Feature
     */
    fun convertToGeoJson(narsFeature: NarsFeature): Feature {
        val geometry = convertGeometryToGeoJson(narsFeature.geometry)

        return Feature(
            id = narsFeature.id,
            geometry = geometry,
            properties =
            mapOf(
                "id" to narsFeature.id,
                "type" to narsFeature.type.value,
                "phase" to narsFeature.properties.phase,
                "name" to (narsFeature.properties.name ?: ""),
                "color" to narsFeature.properties.color,
            ),
        )
    }

    /**
     * Convert NARS geometry to GeoJSON geometry
     */
    fun convertGeometryToGeoJson(geometry: com.nars.maplibre.data.model.Geometry): Geometry = when (geometry) {
        is PointGeometry -> {
            Point.fromLngLat(LngLat(geometry.coordinates[0], geometry.coordinates[1]))
        }

        is LineStringGeometry -> {
            val coords = coordinatesToLngLats(geometry.coordinates)
            LineString.fromLngLats(coords)
        }

        is PolygonGeometry -> {
            val coords = coordinatesToLngLats(geometry.coordinates)
            Polygon.fromLngLats(listOf(coords))
        }

        is CircleGeometry -> {
            // Circle becomes Point for Geoman; radius preserved in NarsFeature.geometry.coordinates[2]
            Point.fromLngLat(LngLat(geometry.coordinates[0], geometry.coordinates[1]))
        }
    }

    /**
     * Convert NarsFeature to Geoman FeatureData
     */
    fun convertToGeomanFeatureData(narsFeature: NarsFeature): FeatureData {
        val geoJsonFeature = convertToGeoJson(narsFeature)
        return FeatureData(
            id = narsFeature.id,
            sourceName = getSourceNameForGeometry(narsFeature.geometry),
            feature = geoJsonFeature,
            properties = mutableMapOf(),
        )
    }

    /**
     * Get the correct Geoman source name for a geometry type
     */
    fun getSourceNameForGeometry(geometry: com.nars.maplibre.data.model.Geometry): String = when (geometry) {
        is PointGeometry -> GeomanCoreConstants.SOURCE_MARKERS
        is LineStringGeometry -> GeomanCoreConstants.SOURCE_LINES
        is PolygonGeometry -> GeomanCoreConstants.SOURCE_POLYGONS
        is CircleGeometry -> GeomanCoreConstants.SOURCE_CIRCLES
    }

    /**
     * Convert GeoJSON geometry to JsonObject (avoids string roundtrip)
     */
    fun geometryToJsonElement(
        geometry: com.geoman.maplibre.geoman.types.geojson.Geometry,
    ): JsonObject = buildJsonObject {
        when (geometry) {
            is Point -> {
                put("type", "Point")
                putJsonArray("coordinates") {
                    add(geometry.coordinates[0])
                    add(geometry.coordinates[1])
                }
            }

            is LineString -> {
                put("type", "LineString")
                putJsonArray("coordinates") {
                    for (coord in geometry.coordinates) {
                        add(
                            buildJsonArray {
                                coord.forEach { add(it) }
                            },
                        )
                    }
                }
            }

            is Polygon -> {
                put("type", "Polygon")
                putJsonArray("coordinates") {
                    for (ring in geometry.coordinates) {
                        add(
                            buildJsonArray {
                                for (coord in ring) {
                                    add(
                                        buildJsonArray {
                                            coord.forEach { add(it) }
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
            }

            else -> {
                put("type", "Point")
                putJsonArray("coordinates") {
                    add(0.0)
                    add(0.0)
                }
            }
        }
    }

    private fun coordinatesToLngLats(coords: List<Double>): List<LngLat> = coords
        .chunked(2)
        .filter {
            it.size == 2
        }.map { LngLat(it[0], it[1]) }

    /**
     * Build GeoJSON LineString for polygon edges
     */
    fun buildPolygonEdgesGeoJson(coordinates: List<Double>): String {
        val points = coordinates.chunked(2).filter { it.size == 2 }
        val ring =
            if (points.firstOrNull() == points.lastOrNull()) {
                points
            } else {
                points + points.firstOrNull()
            }

        return buildJsonObject {
            put("type", "Feature")
            putJsonObject("geometry") {
                put("type", "LineString")
                putJsonArray("coordinates") {
                    add(
                        buildJsonArray {
                            for (coord in ring.filterNotNull()) {
                                add(
                                    buildJsonArray {
                                        add(coord[0])
                                        add(coord[1])
                                    },
                                )
                            }
                        },
                    )
                }
            }
            putJsonObject("properties") { }
        }.toString()
    }

    /**
     * Build GeoJSON Polygon approximating a circle
     */
    fun buildCircleGeoJson(centerLng: Double, centerLat: Double, radiusMeters: Double): String {
        val segments = CIRCLE_APPROXIMATION_SEGMENTS
        val earthRadius = GeometryUtils.EARTH_RADIUS_METERS
        val radiusDegrees = Math.toDegrees(radiusMeters / earthRadius)

        val ring =
            (0..segments).map { i ->
                val angle = Math.toRadians(i * DEGREES_IN_CIRCLE / segments)
                val lng = centerLng + radiusDegrees * Math.cos(angle) / Math.cos(Math.toRadians(centerLat))
                val lat = centerLat + radiusDegrees * Math.sin(angle)
                lng to lat
            }

        return buildJsonObject {
            put("type", "Feature")
            putJsonObject("geometry") {
                put("type", "Polygon")
                putJsonArray("coordinates") {
                    add(
                        buildJsonArray {
                            add(
                                buildJsonArray {
                                    for ((lng, lat) in ring) {
                                        add(
                                            buildJsonArray {
                                                add(lng)
                                                add(lat)
                                            },
                                        )
                                    }
                                },
                            )
                        },
                    )
                }
            }
            putJsonObject("properties") { }
        }.toString()
    }

    /**
     * Build a GeoJSON Feature string from a Geoman Feature, with optional properties.
     */
    fun buildFeatureGeoJson(
        feature: Feature,
        properties: Map<String, Any?>? = feature.properties,
    ): String = buildJsonObject {
        put("type", "Feature")
        put("id", feature.id ?: "")
        put("geometry", geometryToJsonElement(feature.geometry))
        putJsonObject("properties") {
            val mutableProps = properties?.toMutableMap() ?: mutableMapOf()
            if (mutableProps.containsKey("name") && !mutableProps.containsKey("label")) {
                mutableProps["label"] = mutableProps["name"]
            }
            mutableProps.forEach { (key, value) ->
                put(key, value?.toString() ?: "")
            }
        }
    }.toString()
}
