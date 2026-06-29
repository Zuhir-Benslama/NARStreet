package com.nars.maplibre.modes

import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.GeomanCoreConstants
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.nars.maplibre.data.model.FeatureProperties
import com.nars.maplibre.data.model.LineStringGeometry
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.NarsFeatureType
import com.nars.maplibre.data.model.PhaseDefinition
import com.nars.maplibre.data.model.Phases
import com.nars.maplibre.data.model.PointGeometry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import com.geoman.maplibre.geoman.types.geojson.Feature as GeoJsonFeature

class FeatureDisplayManagerTest {
    private lateinit var geoman: Geoman
    private lateinit var featureRenderer: FeatureRenderer
    private lateinit var geometryConverter: GeometryConverter
    private lateinit var map: MapLibreMap
    private lateinit var labelAndMarkerManager: LabelAndMarkerManager
    private lateinit var displayManager: FeatureDisplayManager

    private val roadPhase =
        PhaseDefinition(
            0,
            Phases.ROADS_KEY,
            "roads",
            com.nars.maplibre.data.model.DrawType.POLYLINE,
            "#3498db",
            "",
        )
    private val entrancePhase =
        PhaseDefinition(
            1,
            Phases.HOUSE_ENTRANCES_KEY,
            "entrances",
            com.nars.maplibre.data.model.DrawType.MARKER,
            "#27ae60",
            "",
        )

    @Before
    fun setUp() {
        geoman = mockk(relaxed = true)
        featureRenderer = mockk(relaxed = true)
        geometryConverter = mockk(relaxed = true)
        map = mockk(relaxed = true)
        labelAndMarkerManager = mockk(relaxed = true)
        every { featureRenderer.labelAndMarkerManager } returns labelAndMarkerManager

        displayManager = FeatureDisplayManager(geoman, featureRenderer, geometryConverter, map)
    }

    private fun createRoad(id: String = "road-1"): NarsFeature = NarsFeature(
        id = id,
        type = NarsFeatureType.ROAD,
        geometry = PointGeometry(coordinates = listOf(3.0, 36.0)),
        properties = FeatureProperties.RoadProperties(),
    )

    private fun createEntrance(id: String = "ent-1"): NarsFeature = NarsFeature(
        id = id,
        type = NarsFeatureType.HOUSE_ENTRANCE,
        geometry = PointGeometry(coordinates = listOf(3.1, 36.1)),
        properties = FeatureProperties.HouseEntranceProperties(),
    )

    @Test
    fun `addFeature delegates to renderer and geoman`() {
        val feature = createRoad()
        val geoJsonFeature = mockk<GeoJsonFeature>(relaxed = true)
        every { geometryConverter.convertToGeoJson(feature) } returns geoJsonFeature
        val sourceName = GeomanCoreConstants.SOURCE_MARKERS
        every { geometryConverter.getSourceNameForGeometry(feature.geometry) } returns sourceName

        displayManager.addFeature(feature)

        verify { featureRenderer.addFeature(feature) }
        verify { geometryConverter.convertToGeoJson(feature) }
        verify { geoman.addGeoJsonFeature(geoJsonFeature, GeomanCoreConstants.SOURCE_MARKERS) }
    }

    @Test
    fun `addFeatures filters by current phase`() {
        displayManager.currentPhase = roadPhase
        val road = createRoad()
        val entrance = createEntrance()

        displayManager.addFeatures(listOf(road, entrance))

        verify { featureRenderer.addFeature(road) }
        verify(exactly = 0) { featureRenderer.addFeature(entrance) }
    }

    @Test
    fun `addFeatures with null phase adds all`() {
        val road = createRoad()
        val entrance = createEntrance()

        displayManager.addFeatures(listOf(road, entrance))

        verify { featureRenderer.addFeature(road) }
        verify { featureRenderer.addFeature(entrance) }
    }

    @Test
    fun `updateDisplayedFeatures removes stale features`() {
        displayManager.addFeature(createRoad("r1"))
        val r2 = createRoad("r2")

        displayManager.updateDisplayedFeatures(listOf(r2))

        verify { featureRenderer.removeFromTracking("r1") }
    }

    @Test
    fun `updateDisplayedFeatures adds new features`() {
        val r1 = createRoad("r1")
        displayManager.addFeature(r1)
        val r2 = createRoad("r2")

        displayManager.updateDisplayedFeatures(listOf(r1, r2))

        verify { featureRenderer.addFeature(r2) }
    }

    @Test
    fun `updateDisplayedFeatures calls addRoadEndpointMarkers for road phase`() {
        displayManager.currentPhase = roadPhase
        val features = listOf(createRoad("r1"))

        displayManager.updateDisplayedFeatures(features)

        verify { labelAndMarkerManager.addRoadEndpointMarkers(features) }
    }

    @Test
    fun `updateDisplayedFeatures skips road markers for non-road phase`() {
        displayManager.currentPhase = entrancePhase
        displayManager.updateDisplayedFeatures(listOf(createEntrance()))
        verify(exactly = 0) { labelAndMarkerManager.addRoadEndpointMarkers(any()) }
    }

    @Test
    fun `updateFeatureId ignores same id`() {
        displayManager.addFeature(createRoad("same"))
        displayManager.updateFeatureId("same", "same")

        verify(exactly = 0) { featureRenderer.removeFromTracking(any()) }
    }

    @Test
    fun `updateFeatureId updates tracking`() {
        displayManager.addFeature(createRoad("old"))

        displayManager.updateFeatureId("old", "new")

        verify { featureRenderer.removeFromTracking("old") }
    }

    @Test
    fun `removeFeature removes from geoman and renderer`() {
        val featureData = mockk<FeatureData>(relaxed = true)
        every { geoman.features.getFeature(any(), "r1") } returns featureData
        displayManager.addFeature(createRoad("r1"))

        displayManager.removeFeature("r1")

        verify { geoman.features.removeFeature(any(), "r1") }
        verify { labelAndMarkerManager.removeVertexMarkers("r1") }
        verify { featureRenderer.removeFromTracking("r1") }
    }

    @Test
    fun `clearAllFeatures clears everything`() {
        displayManager.addFeature(createRoad("r1"))
        displayManager.addFeature(createRoad("r2"))

        displayManager.clearAllFeatures()

        verify { geoman.clearAllFeatures() }
        verify { featureRenderer.clearTracking() }
    }
}
