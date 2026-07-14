package com.nars.maplibre.modes

import com.nars.maplibre.data.model.CircleGeometry
import com.nars.maplibre.data.model.LineStringGeometry
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.PointGeometry
import com.nars.maplibre.data.model.PolygonGeometry
import com.nars.maplibre.utils.GeometryUtils
import com.nars.maplibre.utils.NarsLogger
import org.maplibre.android.geometry.LatLng
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class SnappingEngine {
    companion object {
        private const val TAG = "SnappingEngine"
        private const val DEFAULT_SNAP_THRESHOLD_METERS = 20.0
    }

    fun snapPoint(
        point: LatLng,
        features: List<NarsFeature>,
        snapThresholdMeters: Double = DEFAULT_SNAP_THRESHOLD_METERS,
    ): LatLng {
        var closestPoint = point
        var minDistance = snapThresholdMeters

        for (feature in features) {
            when (val geom = feature.geometry) {
                is PointGeometry -> {
                    val result = snapToPoint(point, geom, closestPoint, minDistance)
                    closestPoint = result.first
                    minDistance = result.second
                }

                is LineStringGeometry -> {
                    val result = snapToLineString(point, geom, closestPoint, minDistance)
                    closestPoint = result.first
                    minDistance = result.second
                }

                is PolygonGeometry -> {
                    val result = snapToPolygon(point, geom, closestPoint, minDistance)
                    closestPoint = result.first
                    minDistance = result.second
                }

                is CircleGeometry -> {
                    val result = snapToCircle(point, geom, closestPoint, minDistance)
                    closestPoint = result.first
                    minDistance = result.second
                }
            }
        }

        if (minDistance < snapThresholdMeters) {
            NarsLogger.d(
                TAG,
                "Snapped point: ${point.latitude},${point.longitude} -> " +
                    "${closestPoint.latitude},${closestPoint.longitude} (${minDistance.toInt()}m)",
            )
        }
        return closestPoint
    }

    private fun snapToPoint(
        point: LatLng,
        geometry: PointGeometry,
        currentClosest: LatLng,
        currentMinDist: Double,
    ): Pair<LatLng, Double> {
        val fp = LatLng(geometry.coordinates[1], geometry.coordinates[0])
        val d = point.distanceTo(fp)
        return if (d < currentMinDist) fp to d else currentClosest to currentMinDist
    }

    private fun snapToLineString(
        point: LatLng,
        geometry: LineStringGeometry,
        currentClosest: LatLng,
        currentMinDist: Double,
    ): Pair<LatLng, Double> = snapToCoordPath(point, geometry.coordinates, currentClosest, currentMinDist)

    private fun snapToPolygon(
        point: LatLng,
        geometry: PolygonGeometry,
        currentClosest: LatLng,
        currentMinDist: Double,
    ): Pair<LatLng, Double> = snapToCoordPath(point, geometry.coordinates, currentClosest, currentMinDist)

    private fun snapToCoordPath(
        point: LatLng,
        coords: List<Double>,
        currentClosest: LatLng,
        currentMinDist: Double,
    ): Pair<LatLng, Double> {
        var closest = currentClosest
        var minDist = currentMinDist
        val pairs = coords.chunked(2).filter { it.size == 2 }
        for (i in 0 until pairs.size - 1) {
            val p1 = LatLng(pairs[i][1], pairs[i][0])
            val p2 = LatLng(pairs[i + 1][1], pairs[i + 1][0])
            val snapped = nearestPointOnSegment(point, p1, p2)
            val d = point.distanceTo(snapped)
            if (d < minDist) {
                minDist = d
                closest = snapped
            }
        }
        for (pair in pairs) {
            val vp = LatLng(pair[1], pair[0])
            val d = point.distanceTo(vp)
            if (d < minDist) {
                minDist = d
                closest = vp
            }
        }
        return closest to minDist
    }

    private fun snapToCircle(
        point: LatLng,
        geometry: CircleGeometry,
        currentClosest: LatLng,
        currentMinDist: Double,
    ): Pair<LatLng, Double> {
        val cp = LatLng(geometry.coordinates[1], geometry.coordinates[0])
        val d = point.distanceTo(cp)
        return if (d < currentMinDist) cp to d else currentClosest to currentMinDist
    }

    fun nearestPointOnSegment(point: LatLng, p1: LatLng, p2: LatLng): LatLng {
        val segLength = p1.distanceTo(p2)
        if (segLength < 1.0) return p1

        val d1 = p1.distanceTo(point)
        val d2 = p2.distanceTo(point)

        val bearingAB = bearingDeg(p1, p2)
        val bearingAC = bearingDeg(p1, point)

        val angularDistAC = d1 / GeometryUtils.EARTH_RADIUS_METERS
        val crossTrack = sin(
            (sin(angularDistAC) * sin(Math.toRadians(bearingAC - bearingAB)))
                .coerceIn(-1.0, 1.0),
        )
        val alongTrack = acos(
            (cos(angularDistAC) / cos(crossTrack)).coerceIn(-1.0, 1.0),
        )
        val fraction = (alongTrack / (segLength / GeometryUtils.EARTH_RADIUS_METERS)).coerceIn(0.0, 1.0)

        return interpolateBearing(p1, bearingAB, fraction * segLength)
    }

    private fun bearingDeg(from: LatLng, to: LatLng): Double {
        val dLng = Math.toRadians(to.longitude - from.longitude)
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private fun interpolateBearing(from: LatLng, bearingDeg: Double, distanceMeters: Double): LatLng {
        val angularDist = distanceMeters / GeometryUtils.EARTH_RADIUS_METERS
        val lat1 = Math.toRadians(from.latitude)
        val lng1 = Math.toRadians(from.longitude)
        val brng = Math.toRadians(bearingDeg)

        val lat2 = sin(
            (
                sin(lat1) * cos(angularDist) +
                    cos(lat1) * sin(angularDist) * cos(brng)
                )
                .coerceIn(-1.0, 1.0),
        )
        val lng2 = lng1 + atan2(
            sin(brng) * sin(angularDist) * cos(lat1),
            cos(angularDist) - sin(lat1) * sin(lat2),
        )
        return LatLng(Math.toDegrees(lat2), Math.toDegrees(lng2))
    }
}
