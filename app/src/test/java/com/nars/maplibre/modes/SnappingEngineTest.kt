package com.nars.maplibre.modes

import com.nars.maplibre.data.model.CircleGeometry
import com.nars.maplibre.data.model.FeatureProperties
import com.nars.maplibre.data.model.LineStringGeometry
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.NarsFeatureType
import com.nars.maplibre.data.model.PointGeometry
import com.nars.maplibre.data.model.PolygonGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.maplibre.android.geometry.LatLng

class SnappingEngineTest {
    private lateinit var engine: SnappingEngine

    @Before
    fun setUp() {
        engine = SnappingEngine()
    }

    private fun createPoint(lat: Double, lng: Double, id: String = "p1"): NarsFeature = NarsFeature(
        id = id,
        type = NarsFeatureType.ROAD,
        geometry = PointGeometry(coordinates = listOf(lng, lat)),
        properties = FeatureProperties.RoadProperties(),
    )

    @Test
    fun `snapPoint with no features returns original point`() {
        val point = LatLng(36.0, 3.0)
        val result = engine.snapPoint(point, emptyList(), 20.0)
        assertEquals(point, result)
    }

    @Test
    fun `snapPoint within threshold snaps to nearest point`() {
        val point = LatLng(36.0001, 3.0001)
        val feature = createPoint(36.0, 3.0)
        val result = engine.snapPoint(point, listOf(feature), 50.0)
        assertNotNull(result)
    }

    @Test
    fun `snapPoint outside threshold returns original point`() {
        val point = LatLng(37.0, 4.0)
        val feature = createPoint(36.0, 3.0)
        val result = engine.snapPoint(point, listOf(feature), 20.0)
        assertEquals(point, result)
    }

    @Test
    fun `snapPoint snaps to line string vertex`() {
        val line =
            NarsFeature(
                id = "l1",
                type = NarsFeatureType.ROAD,
                geometry = LineStringGeometry(coordinates = listOf(3.0, 36.0, 3.1, 36.1, 3.2, 36.2)),
                properties = FeatureProperties.RoadProperties(),
            )
        val point = LatLng(36.05, 3.05)
        val result = engine.snapPoint(point, listOf(line), 20000.0)
        assertNotNull(result)
    }

    @Test
    fun `snapPoint snaps to polygon vertex`() {
        val poly =
            NarsFeature(
                id = "pg1",
                type = NarsFeatureType.ROAD,
                geometry = PolygonGeometry(coordinates = listOf(3.0, 36.0, 3.1, 36.1, 3.0, 36.2, 3.0, 36.0)),
                properties = FeatureProperties.RoadProperties(),
            )
        val point = LatLng(36.05, 3.05)
        val result = engine.snapPoint(point, listOf(poly), 20000.0)
        assertNotNull(result)
    }

    @Test
    fun `snapPoint snaps to circle center`() {
        val circle =
            NarsFeature(
                id = "c1",
                type = NarsFeatureType.ROAD,
                geometry = CircleGeometry(coordinates = listOf(3.0, 36.0, 100.0)),
                properties = FeatureProperties.RoadProperties(),
            )
        val point = LatLng(36.001, 3.001)
        val result = engine.snapPoint(point, listOf(circle), 200.0)
        assertNotNull(result)
    }

    @Test
    fun `nearestPointOnSegment with very short segment returns p1`() {
        val p1 = LatLng(36.0, 3.0)
        val p2 = LatLng(36.0000001, 3.0000001)
        val point = LatLng(36.5, 3.5)
        val result = engine.nearestPointOnSegment(point, p1, p2)
        assertEquals(p1, result)
    }

    @Test
    fun `snapPoint with multiple features picks closest`() {
        val far = createPoint(36.0, 3.0, "far")
        val close = createPoint(36.001, 3.001, "close")
        val point = LatLng(36.0011, 3.0011)
        val result = engine.snapPoint(point, listOf(far, close), 200.0)
        val expected = LatLng(36.001, 3.001)
        assertEquals(expected, result)
    }

    @Test
    fun `snapPoint with threshold zero never snaps`() {
        val point = LatLng(36.0, 3.0)
        val feature = createPoint(36.0, 3.0)
        val result = engine.snapPoint(point, listOf(feature), 0.0)
        assertEquals(point, result)
    }
}
