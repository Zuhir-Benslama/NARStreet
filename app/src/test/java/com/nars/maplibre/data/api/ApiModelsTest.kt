package com.nars.maplibre.data.api

import com.nars.maplibre.data.model.CircleGeometry
import com.nars.maplibre.data.model.FeatureProperties
import com.nars.maplibre.data.model.LineStringGeometry
import com.nars.maplibre.data.model.NarsFeatureType
import com.nars.maplibre.data.model.Phases
import com.nars.maplibre.data.model.PointGeometry
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.GregorianCalendar
import java.util.TimeZone

class ApiModelsTest {
    private fun utcCalendar(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long {
        val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"))
        calendar.clear()
        calendar.set(year, month - 1, day, hour, minute, second)
        return calendar.timeInMillis
    }

    // ─── toNarsFeature: geometry parsing ─────────────────────────────────────

    @Test
    fun `road with lat lng maps to PointGeometry with swapped coordinates`() {
        val result =
            ApiFeatureResult(
                id = "42",
                type = "road",
                label = "Main St",
                data = buildJsonObject {
                    put("lat", 10.5)
                    put("lng", 20.25)
                },
            ).toNarsFeature()

        assertNotNull(result)
        assertEquals("42", result!!.id)
        assertEquals("42", result.dbId)
        assertEquals(NarsFeatureType.ROAD, result.type)
        assertEquals(PointGeometry(coordinates = listOf(20.25, 10.5)), result.geometry)
    }

    @Test
    fun `radius plus lat lng maps to CircleGeometry including radius`() {
        val result =
            ApiFeatureResult(
                id = "7",
                type = "naming_panel",
                data = buildJsonObject {
                    put("lat", 1.0)
                    put("lng", 2.0)
                    put("radius", 30.0)
                },
            ).toNarsFeature()

        assertNotNull(result)
        assertEquals(CircleGeometry(coordinates = listOf(2.0, 1.0, 30.0)), result!!.geometry)
        assertEquals(NarsFeatureType.NAMING_PANEL, result.type)
    }

    @Test
    fun `coordinate pairs map to LineStringGeometry flattened as lng lat`() {
        val data = buildJsonObject {
            put(
                "coordinates",
                kotlinx.serialization.json.buildJsonArray {
                    add(
                        buildJsonObject {
                            put("lat", 1.0)
                            put("lng", 11.0)
                        },
                    )
                    add(
                        buildJsonObject {
                            put("lat", 2.0)
                            put("lng", 12.0)
                        },
                    )
                    add(
                        buildJsonObject {
                            put("lat", 3.0)
                            put("lng", 13.0)
                        },
                    )
                },
            )
        }
        val result =
            ApiFeatureResult(id = "9", type = "road", data = data).toNarsFeature()

        assertNotNull(result)
        assertEquals(
            LineStringGeometry(coordinates = listOf(11.0, 1.0, 12.0, 2.0, 13.0, 3.0)),
            result!!.geometry,
        )
    }

    @Test
    fun `single coordinate pair falls back to PointGeometry`() {
        val data = buildJsonObject {
            put(
                "coordinates",
                kotlinx.serialization.json.buildJsonArray {
                    add(
                        buildJsonObject {
                            put("lat", 5.0)
                            put("lng", 6.0)
                        },
                    )
                },
            )
        }
        val result =
            ApiFeatureResult(id = "9", type = "house_entrance", data = data).toNarsFeature()

        assertNotNull(result)
        assertEquals(PointGeometry(coordinates = listOf(6.0, 5.0)), result!!.geometry)
    }

    @Test
    fun `feature without geometry is rejected`() {
        val result =
            ApiFeatureResult(id = "1", type = "road", data = buildJsonObject { }).toNarsFeature()
        assertNull(result)
    }

    @Test
    fun `unknown type is rejected`() {
        val result =
            ApiFeatureResult(id = "1", type = "volcano", data = buildJsonObject { }).toNarsFeature()
        assertNull(result)
    }

    // ─── toNarsFeature: properties ───────────────────────────────────────────

    @Test
    fun `data label wins over top-level label and layer fills roadTypeKey fallback`() {
        val fromData =
            ApiFeatureResult(
                id = "1",
                type = "road",
                layer = "highway",
                label = "top label",
                data = buildJsonObject {
                    put("lat", 0.0)
                    put("lng", 0.0)
                    put("label", "data label")
                },
            ).toNarsFeature()!!

        assertEquals("data label", (fromData.properties as FeatureProperties.RoadProperties).name)
        assertEquals("highway", fromData.properties.roadTypeKey)

        val fromTopLevel =
            ApiFeatureResult(
                id = "2",
                type = "road",
                layer = "street",
                label = "top label",
                data = buildJsonObject {
                    put("lat", 0.0)
                    put("lng", 0.0)
                },
            ).toNarsFeature()!!

        assertEquals("top label", (fromTopLevel.properties as FeatureProperties.RoadProperties).name)
        assertEquals("street", fromTopLevel.properties.roadTypeKey)
    }

    @Test
    fun `each feature type gets its phase key and color`() {
        val base = buildJsonObject {
            put("lat", 0.0)
            put("lng", 0.0)
        }

        val road = ApiFeatureResult(id = "r", type = "road", data = base).toNarsFeature()!!
        val entrance =
            ApiFeatureResult(id = "h", type = "house_entrance", data = base).toNarsFeature()!!
        val panel = ApiFeatureResult(id = "p", type = "naming_panel", data = base).toNarsFeature()!!

        assertEquals(Phases.ROADS_KEY, road.properties.phase)
        assertEquals("#3498db", road.properties.color)
        assertEquals(Phases.HOUSE_ENTRANCES_KEY, entrance.properties.phase)
        assertEquals("#27ae60", entrance.properties.color)
        assertEquals(Phases.NAMING_PANELS_KEY, panel.properties.phase)
        assertEquals("#9b59b6", panel.properties.color)
    }

    @Test
    fun `house entrance parses roadDbId and side`() {
        val entrance =
            ApiFeatureResult(
                id = "h",
                type = "house_entrance",
                data = buildJsonObject {
                    put("lat", 0.0)
                    put("lng", 0.0)
                    put("roadDbId", "99")
                    put("side", "left")
                },
            ).toNarsFeature()!!
        val props = entrance.properties as FeatureProperties.HouseEntranceProperties

        assertEquals("99", props.roadDbId)
        assertEquals("left", props.side)
    }

    // ─── toNarsFeature: timestamp parsing ────────────────────────────────────

    @Test
    fun `ISO timestamp with Z suffix parses as UTC`() {
        val result =
            ApiFeatureResult(
                id = "1",
                type = "road",
                createdAt = "2026-08-24T12:00:00Z",
                data = buildJsonObject {
                    put("lat", 0.0)
                    put("lng", 0.0)
                },
            ).toNarsFeature()!!

        assertEquals(utcCalendar(2026, 8, 24, 12, 0, 0), result.createdAt!!)
    }

    @Test
    fun `ISO timestamp with numeric offset converts to UTC`() {
        val result =
            ApiFeatureResult(
                id = "1",
                type = "road",
                createdAt = "2026-08-24T14:30:00+02:00",
                data = buildJsonObject {
                    put("lat", 0.0)
                    put("lng", 0.0)
                },
            ).toNarsFeature()!!

        assertEquals(utcCalendar(2026, 8, 24, 12, 30, 0), result.createdAt!!)
    }

    @Test
    fun `negative offset timestamp converts to UTC`() {
        val result =
            ApiFeatureResult(
                id = "1",
                type = "road",
                createdAt = "2026-08-24T09:15:00-03:30",
                data = buildJsonObject {
                    put("lat", 0.0)
                    put("lng", 0.0)
                },
            ).toNarsFeature()!!

        assertEquals(utcCalendar(2026, 8, 24, 12, 45, 0), result.createdAt!!)
    }

    @Test
    fun `fractional seconds are accepted`() {
        val result =
            ApiFeatureResult(
                id = "1",
                type = "road",
                createdAt = "2026-08-24T12:00:00.123Z",
                data = buildJsonObject {
                    put("lat", 0.0)
                    put("lng", 0.0)
                },
            ).toNarsFeature()!!

        assertEquals(utcCalendar(2026, 8, 24, 12, 0, 0), result.createdAt!!)
    }

    @Test
    fun `missing timestamp falls back to approximately now`() {
        val before = System.currentTimeMillis()
        val result =
            ApiFeatureResult(
                id = "1",
                type = "road",
                createdAt = null,
                data = buildJsonObject {
                    put("lat", 0.0)
                    put("lng", 0.0)
                },
            ).toNarsFeature()!!
        val after = System.currentTimeMillis()

        val createdAt = result.createdAt
        assertTrue(createdAt != null && createdAt >= before && createdAt <= after + 5_000)
    }

    @Test
    fun `malformed timestamp falls back to approximately now instead of crashing`() {
        val before = System.currentTimeMillis()
        val result =
            ApiFeatureResult(
                id = "1",
                type = "road",
                createdAt = "definitely-not-a-date",
                data = buildJsonObject {
                    put("lat", 0.0)
                    put("lng", 0.0)
                },
            ).toNarsFeature()!!
        val after = System.currentTimeMillis()

        val createdAt = result.createdAt
        assertTrue(createdAt != null && createdAt >= before && createdAt <= after + 5_000)
    }

    @Test
    fun `impossible calendar date falls back without crashing`() {
        val result =
            ApiFeatureResult(
                id = "1",
                type = "road",
                createdAt = "2026-02-30T25:61:61Z",
                data = buildJsonObject {
                    put("lat", 0.0)
                    put("lng", 0.0)
                },
            ).toNarsFeature()!!

        assertTrue(result.createdAt!! > 0)
    }

    // ─── toApiSaveRequest / toApiUpdateRequest ───────────────────────────────

    @Test
    fun `road save request serializes type layer label and geometry payload`() {
        val feature =
            com.nars.maplibre.data.model.NarsFeature(
                id = "client-1",
                type = NarsFeatureType.ROAD,
                geometry = PointGeometry(coordinates = listOf(20.25, 10.5)),
                properties = FeatureProperties.RoadProperties(name = "Main St", roadTypeKey = "avenue"),
            )

        val request = feature.toApiSaveRequest()

        assertEquals("road", request.type)
        assertEquals("avenue", request.layer)
        assertEquals("Main St", request.label)

        val data = request.data as kotlinx.serialization.json.JsonObject
        assertEquals(10.5, data["lat"].toString().toDouble(), 0.0001)
        assertEquals(20.25, data["lng"].toString().toDouble(), 0.0001)
        assertTrue(data.containsKey("coordinates"))
    }

    @Test
    fun `road without roadTypeKey defaults layer to street`() {
        val feature =
            com.nars.maplibre.data.model.NarsFeature(
                id = "client-1",
                type = NarsFeatureType.ROAD,
                geometry = PointGeometry(coordinates = listOf(0.0, 0.0)),
                properties = FeatureProperties.RoadProperties(),
            )

        assertEquals("street", feature.toApiSaveRequest().layer)
    }

    @Test
    fun `update request carries label only when present`() {
        val named =
            com.nars.maplibre.data.model.NarsFeature(
                id = "c1",
                type = NarsFeatureType.ROAD,
                geometry = PointGeometry(coordinates = listOf(0.0, 0.0)),
                properties = FeatureProperties.RoadProperties(name = "Renamed"),
            )
        val unnamed =
            named.copy(properties = FeatureProperties.RoadProperties())

        assertEquals("Renamed", named.toApiUpdateRequest().label)
        assertNull(unnamed.toApiUpdateRequest().label)
    }

    // ─── geometry edge cases in toNarsFeature ──────────────────────────────

    @Test
    fun `coordinate entry with missing lat or lng is filtered out`() {
        val data = buildJsonObject {
            put(
                "coordinates",
                kotlinx.serialization.json.buildJsonArray {
                    add(buildJsonObject { put("lng", 11.0) })
                    add(
                        buildJsonObject {
                            put("lat", 2.0)
                            put("lng", 12.0)
                        },
                    )
                    add(
                        buildJsonObject {
                            put("lat", 3.0)
                            put("lng", 13.0)
                        },
                    )
                },
            )
        }

        val result =
            ApiFeatureResult(id = "9", type = "road", data = data).toNarsFeature()!!

        // two valid pairs -> LineString
        assertEquals(LineStringGeometry(coordinates = listOf(12.0, 2.0, 13.0, 3.0)), result.geometry)
    }

    @Test
    fun `coordinate entry that is not an object is filtered out`() {
        val data = buildJsonObject {
            put(
                "coordinates",
                kotlinx.serialization.json.buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("junk"))
                    add(
                        buildJsonObject {
                            put("lat", 9.0)
                            put("lng", 19.0)
                        },
                    )
                },
            )
        }

        val result =
            ApiFeatureResult(id = "9", type = "road", data = data).toNarsFeature()!!

        // a single valid pair -> PointGeometry
        assertEquals(PointGeometry(coordinates = listOf(19.0, 9.0)), result.geometry)
    }

    @Test
    fun `data with non-object json falls back to empty object`() {
        val result =
            ApiFeatureResult(id = "1", type = "road", data = kotlinx.serialization.json.JsonPrimitive("str"))
                .toNarsFeature()
        assertNull(result)
    }

    // ─── toApiData for each feature type ───────────────────────────────────

    @Test
    fun `circle toApiData includes radius`() {
        val feature =
            com.nars.maplibre.data.model.NarsFeature(
                id = "c1",
                type = NarsFeatureType.NAMING_PANEL,
                geometry = CircleGeometry(coordinates = listOf(2.0, 1.0, 30.0)),
                properties = FeatureProperties.NamingPanelProperties(name = "Circle"),
            )

        val data = feature.toApiData()

        assertEquals(30.0, data["radius"].toString().toDouble(), 0.0001)
        assertEquals(2.0, data["lng"].toString().toDouble(), 0.0001)
        assertEquals(1.0, data["lat"].toString().toDouble(), 0.0001)
    }

    @Test
    fun `house entrance toApiData includes property keys`() {
        val feature =
            com.nars.maplibre.data.model.NarsFeature(
                id = "h1",
                type = NarsFeatureType.HOUSE_ENTRANCE,
                geometry = PointGeometry(coordinates = listOf(2.0, 1.0)),
                properties =
                FeatureProperties.HouseEntranceProperties(
                    name = "Entrance",
                    entranceTypeKey = "main",
                    roadDbId = "r99",
                    side = "left",
                ),
            )

        val data = feature.toApiData()

        assertEquals("house_entrance", data["type"].toString().replace("\"", ""))
        assertEquals("main", data["entranceTypeKey"]?.toString()?.replace("\"", ""))
        assertEquals("r99", data["roadDbId"]?.toString()?.replace("\"", ""))
        assertEquals("left", data["side"]?.toString()?.replace("\"", ""))
    }

    @Test
    fun `naming panel save request defaults layer`() {
        val feature =
            com.nars.maplibre.data.model.NarsFeature(
                id = "n1",
                type = NarsFeatureType.NAMING_PANEL,
                geometry = PointGeometry(coordinates = listOf(2.0, 1.0)),
                properties = FeatureProperties.NamingPanelProperties(),
            )

        assertEquals("naming_panel", feature.toApiSaveRequest().layer)
        assertEquals("naming_panel", feature.toApiSaveRequest().type)
    }

    @Test
    fun `house entrance save request defaults layer to main_entrance`() {
        val feature =
            com.nars.maplibre.data.model.NarsFeature(
                id = "h1",
                type = NarsFeatureType.HOUSE_ENTRANCE,
                geometry = PointGeometry(coordinates = listOf(2.0, 1.0)),
                properties = FeatureProperties.HouseEntranceProperties(),
            )

        assertEquals("main_entrance", feature.toApiSaveRequest().layer)
    }

    @Test
    fun `naming panel deserializes data label`() {
        val result =
            ApiFeatureResult(
                id = "p1",
                type = "naming_panel",
                data = buildJsonObject {
                    put("lat", 1.0)
                    put("lng", 2.0)
                    put("label", "Panel A")
                },
            ).toNarsFeature()!!

        val props = result.properties as FeatureProperties.NamingPanelProperties
        assertEquals("Panel A", props.name)
        assertEquals(Phases.NAMING_PANELS_KEY, props.phase)
    }

    // ─── ApiProblemDetails serialization ───────────────────────────────────

    @Test
    fun `ApiProblemDetails deserializes problem details`() {
        val decoded =
            kotlinx.serialization.json.Json
                .decodeFromString(
                    ApiProblemDetails.serializer(),
                    """{"title":"Unauthorized","status":401,"detail":"bad credentials"}""",
                )
        assertEquals("Unauthorized", decoded.title)
        assertEquals(401, decoded.status)
        assertEquals("bad credentials", decoded.detail)
    }

    @Test
    fun `ApiProblemDetails defaults are null`() {
        val decoded =
            kotlinx.serialization.json.Json.decodeFromString(ApiProblemDetails.serializer(), """{}""")
        assertNull(decoded.title)
        assertNull(decoded.status)
        assertNull(decoded.detail)
    }
}
