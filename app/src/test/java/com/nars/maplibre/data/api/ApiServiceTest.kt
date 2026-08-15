package com.nars.maplibre.data.api

import com.nars.maplibre.AppPreferences
import com.nars.maplibre.data.model.FeatureProperties
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.NarsFeatureType
import com.nars.maplibre.data.model.PointGeometry
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiServiceTest {
    private val appPreferences: AppPreferences = mockk()
    private lateinit var apiService: ApiService
    private lateinit var engine: MockEngine

    @Before
    fun setUp() {
        engine =
            MockEngine { _ ->
                respond(
                    content = """{"success": true}""",
                    status = HttpStatusCode.OK,
                )
            }
        val client =
            HttpClient(engine) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }
            }
        every { appPreferences.authToken } returns null
        every { appPreferences.refreshToken } returns null
        every { appPreferences.isLoggedIn } returns false
        every { appPreferences.authToken = any() } just Runs
        every { appPreferences.refreshToken = any() } just Runs
        apiService = ApiService(client, appPreferences)
    }

    @Test
    fun `loadFeatures returns empty list for blank body`() = runTest {
        engine =
            MockEngine { _ ->
                respond(
                    content = "",
                    status = HttpStatusCode.OK,
                )
            }
        val client =
            HttpClient(engine) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }
            }
        apiService = ApiService(client, appPreferences)

        val result = apiService.loadFeatures()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.isEmpty() == true)
    }

    @Test
    fun `loadFeatures parses road feature from envelope`() = runTest {
        engine =
            MockEngine { _ ->
                respond(
                    content =
                    """{"features": [
                        {"id": "1", "type": "road", "layer": "street", "label": "Test Road", "data": {"type": "roads", "label": "Test Road", "coordinates": [{"lat": 36.0, "lng": 3.0}, {"lat": 36.1, "lng": 3.1}]}},
                        {"id": "2", "type": "house_entrance", "layer": "main_entrance", "data": {"type": "houseEntrances", "coordinates": [{"lat": 36.2, "lng": 3.2}], "roadTypeKey": "main"}},
                        {"id": "3", "type": "naming_panel", "data": {"type": "namingPanels", "lat": 36.3, "lng": 3.3}}
                    ], "count": 3, "skip": 0, "take": 100}""",
                    status = HttpStatusCode.OK,
                )
            }
        val client =
            HttpClient(engine) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }
            }
        apiService = ApiService(client, appPreferences)

        val result = apiService.loadFeatures()

        assertTrue(result.isSuccess)
        val features = result.getOrNull()
        assertEquals(3, features?.size)
        assertEquals("1", features?.get(0)?.id)
        assertEquals(NarsFeatureType.ROAD, features?.get(0)?.type)
        assertEquals(NarsFeatureType.HOUSE_ENTRANCE, features?.get(1)?.type)
        assertEquals(NarsFeatureType.NAMING_PANEL, features?.get(2)?.type)
        assertEquals("Test Road", features?.get(0)?.properties?.name)
    }

    @Test
    fun `loadFeatures parses empty envelope`() = runTest {
        engine =
            MockEngine { _ ->
                respond(
                    content = """{"features": [], "count": 0, "skip": 0, "take": 100}""",
                    status = HttpStatusCode.OK,
                )
            }
        val client =
            HttpClient(engine) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }
            }
        apiService = ApiService(client, appPreferences)

        val result = apiService.loadFeatures()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.isEmpty() == true)
    }

    @Test
    fun `saveFeature returns id from response`() = runTest {
        val feature =
            NarsFeature(
                id = "local-1",
                type = NarsFeatureType.ROAD,
                geometry = PointGeometry(coordinates = listOf(3.0, 36.0)),
                properties = FeatureProperties.RoadProperties(),
            )
        engine =
            MockEngine { _ ->
                respond(
                    content = """{"id": "server-42"}""",
                    status = HttpStatusCode.OK,
                )
            }
        val client =
            HttpClient(engine) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }
            }
        apiService = ApiService(client, appPreferences)

        val result = apiService.saveFeature(feature)

        assertTrue(result.isSuccess)
        assertEquals("server-42", result.getOrNull())
    }

    @Test
    fun `updateFeature returns success`() = runTest {
        val feature =
            NarsFeature(
                id = "feature-1",
                type = NarsFeatureType.ROAD,
                geometry = PointGeometry(coordinates = listOf(3.0, 36.0)),
                properties = FeatureProperties.RoadProperties(),
            )
        engine =
            MockEngine { _ ->
                respond(
                    content = """{"id": "feature-1"}""",
                    status = HttpStatusCode.OK,
                )
            }
        val client =
            HttpClient(engine) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }
            }
        apiService = ApiService(client, appPreferences)

        val result = apiService.updateFeature("feature-1", feature)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `deleteFeature returns success`() = runTest {
        engine =
            MockEngine { _ ->
                respond(
                    content = "",
                    status = HttpStatusCode.OK,
                )
            }
        val client =
            HttpClient(engine) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }
            }
        apiService = ApiService(client, appPreferences)

        val result = apiService.deleteFeature("feature-1")

        assertTrue(result.isSuccess)
    }
}
