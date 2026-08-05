package com.nars.maplibre.utils

import com.nars.maplibre.data.model.CircleGeometry
import com.nars.maplibre.data.model.FeatureProperties
import com.nars.maplibre.data.model.Geometry
import com.nars.maplibre.data.model.LineStringGeometry
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.NarsFeatureType
import com.nars.maplibre.data.model.PointGeometry
import com.nars.maplibre.data.model.PolygonGeometry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.geometry.LatLng

class FeatureHitTestTest {
    private fun feature(geometry: Geometry): NarsFeature = NarsFeature(
        id = "test",
        type = NarsFeatureType.ROAD,
        geometry = geometry,
        properties = FeatureProperties.RoadProperties(),
    )

    @Test
    fun `point is hit within threshold`() {
        val f = feature(PointGeometry(coordinates = listOf(3.0, 36.0)))

        assertTrue(isPointNearFeature(LatLng(36.0, 3.0001), f))
        assertFalse(isPointNearFeature(LatLng(36.0, 3.001), f))
    }

    @Test
    fun `line is hit at segment middle, not only at vertices`() {
        val f = feature(LineStringGeometry(coordinates = listOf(3.0, 36.0, 3.01, 36.0)))

        assertTrue(isPointNearFeature(LatLng(36.0001, 3.005), f))
        assertFalse(isPointNearFeature(LatLng(36.0005, 3.005), f))
        assertFalse(isPointNearFeature(LatLng(36.0, 3.02), f))
    }

    @Test
    fun `polygon is hit inside and near its edges`() {
        val f =
            feature(
                PolygonGeometry(
                    coordinates = listOf(3.0, 36.0, 3.01, 36.0, 3.01, 36.01, 3.0, 36.01, 3.0, 36.0),
                ),
            )

        assertTrue(isPointNearFeature(LatLng(36.005, 3.005), f))
        assertTrue(isPointNearFeature(LatLng(36.005, 3.0101), f))
        assertFalse(isPointNearFeature(LatLng(36.005, 3.011), f))
    }

    @Test
    fun `polygon without closing vertex still detects interior`() {
        val f =
            feature(
                PolygonGeometry(coordinates = listOf(3.0, 36.0, 3.01, 36.0, 3.01, 36.01, 3.0, 36.01)),
            )

        assertTrue(isPointNearFeature(LatLng(36.005, 3.005), f))
    }

    @Test
    fun `circle is hit within radius`() {
        val f = feature(CircleGeometry(coordinates = listOf(3.0, 36.0, 50.0)))

        assertTrue(isPointNearFeature(LatLng(36.0, 3.0003), f))
        assertFalse(isPointNearFeature(LatLng(36.0, 3.001), f))
    }

    @Test
    fun `malformed coordinates never hit`() {
        assertFalse(isPointNearFeature(LatLng(36.0, 3.0001), feature(LineStringGeometry(coordinates = listOf(3.0)))))
        assertFalse(isPointNearFeature(LatLng(36.0, 3.0001), feature(PolygonGeometry(coordinates = listOf(3.0)))))
        assertFalse(isPointNearFeature(LatLng(36.0, 3.0001), feature(PointGeometry(coordinates = listOf(3.0)))))
    }
}
