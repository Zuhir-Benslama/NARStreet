package com.nars.maplibre.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NarsFeatureTest {

    // ─── NarsFeatureType ───────────────────────────────────────────────────

    @Test
    fun `fromValue maps known values`() {
        assertEquals(NarsFeatureType.ROAD, NarsFeatureType.fromValue("road"))
        assertEquals(NarsFeatureType.HOUSE_ENTRANCE, NarsFeatureType.fromValue("house_entrance"))
        assertEquals(NarsFeatureType.NAMING_PANEL, NarsFeatureType.fromValue("naming_panel"))
    }

    @Test
    fun `fromValue returns null for unknown value`() {
        assertNull(NarsFeatureType.fromValue("volcano"))
    }

    @Test
    fun `enum values expose wire names`() {
        assertEquals("road", NarsFeatureType.ROAD.value)
        assertEquals("house_entrance", NarsFeatureType.HOUSE_ENTRANCE.value)
        assertEquals("naming_panel", NarsFeatureType.NAMING_PANEL.value)
    }

    // ─── Geometry subtype serialization ────────────────────────────────────

    @Test
    fun `geometry subtypes serialize and round-trip via concrete serializers`() {
        val json = Json { ignoreUnknownKeys = true }

        val point = PointGeometry(coordinates = listOf(1.0, 2.0))
        assertEquals(
            point,
            json.decodeFromString(PointGeometry.serializer(), json.encodeToString(PointGeometry.serializer(), point)),
        )

        val line = LineStringGeometry(coordinates = listOf(1.0, 2.0))
        assertEquals(
            line,
            json.decodeFromString(
                LineStringGeometry.serializer(),
                json.encodeToString(LineStringGeometry.serializer(), line),
            ),
        )

        val polygon = PolygonGeometry(coordinates = listOf(1.0, 2.0))
        assertEquals(
            polygon,
            json.decodeFromString(
                PolygonGeometry.serializer(),
                json.encodeToString(PolygonGeometry.serializer(), polygon),
            ),
        )

        val circle = CircleGeometry(coordinates = listOf(1.0, 2.0, 30.0))
        assertEquals(
            circle,
            json.decodeFromString(
                CircleGeometry.serializer(),
                json.encodeToString(CircleGeometry.serializer(), circle),
            ),
        )
    }

    @Test
    fun `geometry subtypes deserialize by serial name`() {
        val json = Json { ignoreUnknownKeys = true }

        val point =
            json.decodeFromString(Geometry.serializer(), """{"type":"Point","coordinates":[1.0,2.0]}""")
        assertTrue(point is PointGeometry)

        val line =
            json.decodeFromString(Geometry.serializer(), """{"type":"LineString","coordinates":[1.0,2.0]}""")
        assertTrue(line is LineStringGeometry)

        val polygon =
            json.decodeFromString(Geometry.serializer(), """{"type":"Polygon","coordinates":[1.0,2.0]}""")
        assertTrue(polygon is PolygonGeometry)

        val circle =
            json.decodeFromString(Geometry.serializer(), """{"type":"Circle","coordinates":[1.0,2.0]}""")
        assertTrue(circle is CircleGeometry)
    }

    @Test
    fun `feature properties seal deserialize by serial name`() {
        val json = Json { ignoreUnknownKeys = true }

        val road =
            json.decodeFromString(
                FeatureProperties.serializer(),
                """{"type":"roads","name":"Main St","phase":"roads","color":"#3498db"}""",
            )
        assertTrue(road is FeatureProperties.RoadProperties)

        val entrance =
            json.decodeFromString(
                FeatureProperties.serializer(),
                """{"type":"houseEntrances","phase":"houseEntrances","color":"#27ae60"}""",
            )
        assertTrue(entrance is FeatureProperties.HouseEntranceProperties)

        val panel =
            json.decodeFromString(
                FeatureProperties.serializer(),
                """{"type":"namingPanels","phase":"namingPanels","color":"#9b59b6"}""",
            )
        assertTrue(panel is FeatureProperties.NamingPanelProperties)
    }

    // ─── FeatureProperties defaults ────────────────────────────────────────

    @Test
    fun `road properties default to roads phase and color`() {
        val props = FeatureProperties.RoadProperties()
        assertNull(props.name)
        assertEquals(Phases.ROADS_KEY, props.phase)
        assertEquals(Phases.ROADS_COLOR, props.color)
        assertNull(props.roadTypeKey)
        assertNull(props.roadTraffic)
        assertNull(props.tradActivity)
        assertNull(props.numLanes)
        assertNull(props.hasMedian)
        assertNull(props.hasVegetation)
        assertNull(props.isDeadEnd)
        assertNull(props.hasSidewalk)
    }

    @Test
    fun `house entrance properties default to entrances phase and color`() {
        val props = FeatureProperties.HouseEntranceProperties()
        assertEquals(Phases.HOUSE_ENTRANCES_KEY, props.phase)
        assertEquals(Phases.HOUSE_ENTRANCES_COLOR, props.color)
        assertNull(props.entranceTypeKey)
        assertNull(props.roadDbId)
        assertNull(props.side)
    }

    @Test
    fun `naming panel properties default to panels phase and color`() {
        val props = FeatureProperties.NamingPanelProperties()
        assertEquals(Phases.NAMING_PANELS_KEY, props.phase)
        assertEquals(Phases.NAMING_PANELS_COLOR, props.color)
        assertNull(props.hasNamingPanelLocation)
        assertNull(props.hasNamingPanel)
        assertNull(props.namingCorrect)
    }

    // ─── PhaseDefinition.parsedColor ───────────────────────────────────────

    @Test
    fun `parsedColor is computed lazily for a valid hex color`() {
        val def = PhaseDefinition(0, "roads", "label", DrawType.POLYLINE, "#3498db", "hint")
        // accessing triggers the lazy Color conversion and must not throw
        assertNotNull(def.parsedColor)
        assertEquals(def.parsedColor, def.parsedColor)
    }

    @Test
    fun `parsedColor handles arbitrary input without throwing`() {
        val def = PhaseDefinition(1, "bad", "label", DrawType.MARKER, "not-a-color", "hint")
        // input may be a valid or invalid color depending on the runtime; the lazy
        // conversion must complete without throwing either way
        assertNotNull(def.parsedColor)
    }

    // ─── Phases ────────────────────────────────────────────────────────────

    @Test
    fun `phases ALL contains the three canonical phases in order`() {
        assertEquals(3, Phases.ALL.size)
        assertEquals(Phases.ROADS_KEY, Phases.ALL[0].key)
        assertEquals(DrawType.POLYLINE, Phases.ALL[0].drawType)
        assertEquals(Phases.HOUSE_ENTRANCES_KEY, Phases.ALL[1].key)
        assertEquals(Phases.NAMING_PANELS_KEY, Phases.ALL[2].key)
    }

    @Test
    fun `getByKey returns the matching phase`() {
        assertEquals(Phases.ALL[0], Phases.getByKey(Phases.ROADS_KEY))
        assertEquals(Phases.ALL[1], Phases.getByKey(Phases.HOUSE_ENTRANCES_KEY))
        assertEquals(Phases.ALL[2], Phases.getByKey(Phases.NAMING_PANELS_KEY))
    }

    @Test
    fun `getByKey returns null for unknown key`() {
        assertNull(Phases.getByKey("bogus"))
    }

    @Test
    fun `getIndexByKey returns correct indices`() {
        assertEquals(0, Phases.getIndexByKey(Phases.ROADS_KEY))
        assertEquals(1, Phases.getIndexByKey(Phases.HOUSE_ENTRANCES_KEY))
        assertEquals(2, Phases.getIndexByKey(Phases.NAMING_PANELS_KEY))
        assertEquals(-1, Phases.getIndexByKey("bogus"))
    }

    @Test
    fun `getByIndex returns phases and null out of range`() {
        assertEquals(Phases.ALL[0], Phases.getByIndex(0))
        assertEquals(Phases.ALL[2], Phases.getByIndex(2))
        assertNull(Phases.getByIndex(-1))
        assertNull(Phases.getByIndex(3))
    }
}
