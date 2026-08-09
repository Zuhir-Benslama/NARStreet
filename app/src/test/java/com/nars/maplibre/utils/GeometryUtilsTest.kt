package com.nars.maplibre.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.geometry.LatLng

class GeometryUtilsTest {
    @Test
    fun `haversineDistance returns zero for identical coordinates`() {
        assertEquals(0.0, GeometryUtils.haversineDistance(LAT, LNG, LAT, LNG), DELTA)
    }

    @Test
    fun `haversineDistance returns about 111 km per degree of latitude`() {
        val distance = GeometryUtils.haversineDistance(0.0, 0.0, 1.0, 0.0)
        assertEquals(KM_PER_DEGREE_LAT, distance, TOLERANCE_METERS)
    }

    @Test
    fun `haversineDistance is symmetric`() {
        val forward = GeometryUtils.haversineDistance(0.0, 0.0, 1.0, 1.0)
        val backward = GeometryUtils.haversineDistance(1.0, 1.0, 0.0, 0.0)
        assertEquals(forward, backward, DELTA)
    }

    @Test
    fun `calculateDistance uses haversine for LatLng pairs`() {
        val distance = GeometryUtils.calculateDistance(LatLng(0.0, 0.0), LatLng(1.0, 1.0))
        assertEquals(DIAGONAL_KM_METERS, distance, TOLERANCE_METERS)
    }

    @Test
    fun `distance grows with separation`() {
        val near = GeometryUtils.haversineDistance(0.0, 0.0, 0.0, 1.0)
        val far = GeometryUtils.haversineDistance(0.0, 0.0, 0.0, 2.0)
        assertTrue(far > near)
    }

    private companion object {
        const val LAT = 28.0
        const val LNG = 2.5
        const val DELTA = 0.0001
        const val KM_PER_DEGREE_LAT = 111195.0
        const val DIAGONAL_KM_METERS = 157200.0
        const val TOLERANCE_METERS = 500.0
    }
}
