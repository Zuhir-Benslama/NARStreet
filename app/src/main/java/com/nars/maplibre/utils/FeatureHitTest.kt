package com.nars.maplibre.utils

import com.nars.maplibre.data.model.CircleGeometry
import com.nars.maplibre.data.model.LineStringGeometry
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.PointGeometry
import com.nars.maplibre.data.model.PolygonGeometry
import org.maplibre.android.geometry.LatLng
import kotlin.math.cos
import kotlin.math.hypot

const val DEFAULT_HIT_THRESHOLD_METERS = 20.0
const val DEFAULT_MIN_CIRCLE_RADIUS_METERS = 10.0

private const val METERS_PER_DEGREE_LATITUDE = 111_320.0
private const val RADIANS_PER_DEGREE = 0.017453292519943295
private const val SEGMENT_T_MIN = 0.0
private const val SEGMENT_T_MAX = 1.0

/**
 * Determines whether a tapped location hits a feature.
 *
 * Points and circles use geodesic distance to the geometry center. Lines and
 * polygons are matched against the nearest segment (not just vertices), so a
 * tap along a long edge or inside a polygon registers even when far from any
 * vertex.
 */
fun isPointNearFeature(
    latLng: LatLng,
    feature: NarsFeature,
    thresholdMeters: Double = DEFAULT_HIT_THRESHOLD_METERS,
    minCircleRadiusMeters: Double = DEFAULT_MIN_CIRCLE_RADIUS_METERS,
): Boolean = when (val geometry = feature.geometry) {
    is PointGeometry -> {
        val center = latLngFrom(geometry.coordinates)
        center != null && latLng.distanceTo(center) < thresholdMeters
    }

    is CircleGeometry -> {
        val center = latLngFrom(geometry.coordinates)
        if (center == null) {
            false
        } else {
            val radius =
                (geometry.coordinates.getOrNull(2) ?: minCircleRadiusMeters)
                    .coerceAtLeast(minCircleRadiusMeters)
            latLng.distanceTo(center) < radius
        }
    }

    is LineStringGeometry -> {
        coordinatePairs(geometry.coordinates)
            .zipWithNext()
            .any { (a, b) -> distanceToSegmentMeters(latLng, a, b) < thresholdMeters }
    }

    is PolygonGeometry -> {
        val ring = closeRing(coordinatePairs(geometry.coordinates))
        isPointInPolygon(latLng, ring) ||
            ring.zipWithNext().any { (a, b) ->
                distanceToSegmentMeters(latLng, a, b) < thresholdMeters
            }
    }
}

private fun latLngFrom(coordinates: List<Double>): LatLng? {
    val lon = coordinates.getOrNull(0) ?: return null
    val lat = coordinates.getOrNull(1) ?: return null
    return LatLng(lat, lon)
}

private fun coordinatePairs(coordinates: List<Double>): List<LatLng> = coordinates.chunked(2).mapNotNull { pair ->
    if (pair.size == 2) LatLng(pair[1], pair[0]) else null
}

private fun closeRing(vertices: List<LatLng>): List<LatLng> =
    if (vertices.size > 1 && vertices.first() != vertices.last()) {
        vertices + vertices.first()
    } else {
        vertices
    }

private fun isPointInPolygon(point: LatLng, polygon: List<LatLng>): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var previous = polygon.size - 1
    for (current in polygon.indices) {
        val a = polygon[previous]
        val b = polygon[current]
        if ((a.latitude > point.latitude) != (b.latitude > point.latitude) &&
            point.longitude <
            (b.longitude - a.longitude) * (point.latitude - a.latitude) /
            (b.latitude - a.latitude) + a.longitude
        ) {
            inside = !inside
        }
        previous = current
    }
    return inside
}

private fun distanceToSegmentMeters(point: LatLng, a: LatLng, b: LatLng): Double {
    val metersPerDegreeLng = METERS_PER_DEGREE_LATITUDE * cos(point.latitude * RADIANS_PER_DEGREE)
    val px = point.longitude * metersPerDegreeLng
    val py = point.latitude * METERS_PER_DEGREE_LATITUDE
    val ax = a.longitude * metersPerDegreeLng
    val ay = a.latitude * METERS_PER_DEGREE_LATITUDE
    val bx = b.longitude * metersPerDegreeLng
    val by = b.latitude * METERS_PER_DEGREE_LATITUDE

    val dx = bx - ax
    val dy = by - ay
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared == SEGMENT_T_MIN) return hypot(px - ax, py - ay)

    val t =
        (((px - ax) * dx + (py - ay) * dy) / lengthSquared)
            .coerceIn(SEGMENT_T_MIN, SEGMENT_T_MAX)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}
