package com.nars.maplibre.modes

import com.nars.maplibre.data.model.FeatureProperties
import com.nars.maplibre.data.model.LineStringGeometry
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.NarsFeatureType
import com.nars.maplibre.data.model.PointGeometry
import com.nars.maplibre.data.model.PolygonGeometry
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.Layer

class LabelAndMarkerManagerTest {
    private lateinit var map: MapLibreMap
    private lateinit var manager: LabelAndMarkerManager

    @Before
    fun setUp() {
        map = mockk(relaxed = true)
        manager = LabelAndMarkerManager(map)
    }

    @Test
    fun `addLabelLayer with null text does nothing`() {
        manager.addLabelLayer("layer", "source", null)
        verify(exactly = 0) { map.style?.addLayer(any<Layer>()) }
    }

    @Test
    fun `addLabelLayer with blank text does nothing`() {
        manager.addLabelLayer("layer", "source", "")
        verify(exactly = 0) { map.style?.addLayer(any<Layer>()) }
    }

    @Test
    fun `addRoadEndpointMarkers does not crash for non-road geometry`() {
        val pointFeature =
            NarsFeature(
                id = "p1",
                type = NarsFeatureType.ROAD,
                geometry = PointGeometry(coordinates = listOf(3.0, 36.0)),
                properties = FeatureProperties.RoadProperties(name = "Point"),
            )
        manager.addRoadEndpointMarkers(listOf(pointFeature))
    }

    @Test
    fun `addVertexMarkers with PointGeometry does not add sources`() {
        val point =
            NarsFeature(
                id = "p1",
                type = NarsFeatureType.ROAD,
                geometry = PointGeometry(coordinates = listOf(3.0, 36.0)),
                properties = FeatureProperties.RoadProperties(),
            )
        manager.addVertexMarkers(point)
        verify(exactly = 0) { map.style?.addSource(any<org.maplibre.android.style.sources.Source>()) }
    }

    @Test
    fun `removeVertexMarkers does nothing if no markers tracked`() {
        manager.removeVertexMarkers("nonexistent")
        verify(exactly = 0) { map.style?.removeLayer(any<Layer>()) }
        verify(exactly = 0) { map.style?.removeSource(any<String>()) }
    }
}
