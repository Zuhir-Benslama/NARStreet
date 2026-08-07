package com.nars.maplibre.data.api

import com.nars.maplibre.AppPreferences
import com.nars.maplibre.data.model.FeatureProperties
import com.nars.maplibre.data.model.NarsFeature
import com.nars.maplibre.data.model.NarsFeatureType
import com.nars.maplibre.data.model.Phases
import com.nars.maplibre.data.model.PointGeometry
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
                    content = """{"success": true, "user": {"id": "1", "username": "test", "name": "Test"}}""",
                    status = HttpStatusCode.OK,
                    headers =
                    io.ktor.http.headersOf(
                        "Set-Cookie",
                        "access_token=test123; refresh_token=refresh456",
                    ),
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
        every { appPreferences.isLoggedIn } returns false
        every { appPreferences.authToken = any() } just Runs
        every { appPreferences.refreshToken = any() } just Runs
        apiService = ApiService(client, appPreferences)
    }

    @Test
    fun `login parses response correctly`() = runTest {
        val result = apiService.login("testuser", "password")

        assertTrue(result.isSuccess)
        val loginResponse = result.getOrNull()
        assertNotNull(loginResponse)
        assertEquals("Test", loginResponse?.user?.name)
        assertEquals("test", loginResponse?.user?.username)
    }

    @Test
    fun `login extracts cookies from response headers`() = runTest {
        val result = apiService.login("testuser", "password")

        assertTrue(result.isSuccess)
        assertEquals("test123", apiService.getSessionToken())
        assertEquals("refresh456", apiService.getRefreshToken())
    }

    @Test
    fun `login persists tokens to preferences`() = runTest {
        val result = apiService.login("testuser", "password")

        assertTrue(result.isSuccess)
        verify { appPreferences.authToken = "test123" }
        verify { appPreferences.refreshToken = "refresh456" }
    }

    @Test
    fun `login handles failure response`() = runTest {
        engine =
            MockEngine { _ ->
                respond(
                    content = """{"success": false, "message": "Invalid credentials"}""",
                    status = HttpStatusCode.Unauthorized,
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

        val result = apiService.login("testuser", "wrong")

        assertTrue(result.isFailure)
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
    fun `setSessionToken and getSessionToken round trip`() {
        apiService.setSessionToken("token123")
        assertEquals("token123", apiService.getSessionToken())
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
    fun `loadFeatures refreshes access token on 401 and retries`() = runTest {
        var callCount = 0
        engine =
            MockEngine { request ->
                callCount++
                when {
                    request.url.encodedPath == "/api/refresh" ->
                        respond(
                            content = """{"success": true}""",
                            status = HttpStatusCode.OK,
                            headers =
                            io.ktor.http.headersOf(
                                "Set-Cookie",
                                "access_token=new-access; refresh_token=new-refresh",
                            ),
                        )

                    callCount == 1 ->
                        respond(
                            content = "",
                            status = HttpStatusCode.Unauthorized,
                        )

                    else ->
                        respond(
                            content = """{"features": [], "count": 0, "skip": 0, "take": 100}""",
                            status = HttpStatusCode.OK,
                        )
                }
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
        apiService.setRefreshToken("refresh-123")

        val result = apiService.loadFeatures()

        assertTrue(result.isSuccess)
        assertEquals(3, callCount)
        assertEquals("new-access", apiService.getSessionToken())
        assertEquals("new-refresh", apiService.getRefreshToken())
    }

    @Test
    fun `loadFeatures fails on 401 when no refresh token is available`() = runTest {
        engine =
            MockEngine { _ ->
                respond(
                    content = "",
                    status = HttpStatusCode.Unauthorized,
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

        assertTrue(result.isFailure)
    }

    @Test
    fun `concurrent 401s never present a consumed refresh token and both requests succeed`() = runBlocking {
        val presented = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val bothArrived = java.util.concurrent.CountDownLatch(2)
        engine =
            MockEngine { request ->
                when {
                    request.url.encodedPath == "/api/refresh" -> {
                        val cookie = request.headers[HttpHeaders.Cookie].orEmpty()
                        val token =
                            Regex("refresh_token=([^;]+)").find(cookie)?.groupValues?.get(1).orEmpty()
                        presented.add(token)
                        respond(
                            content = """{"success": true}""",
                            status = HttpStatusCode.OK,
                            headers = io.ktor.http.headersOf(
                                "Set-Cookie",
                                "access_token=new-access; refresh_token=new-refresh",
                            ),
                        )
                    }

                    request.headers[HttpHeaders.Authorization] == "Bearer old-access" -> {
                        bothArrived.countDown()
                        bothArrived.await(5, java.util.concurrent.TimeUnit.SECONDS)
                        respond(content = "", status = HttpStatusCode.Unauthorized)
                    }

                    else -> respond(
                        content = """{"features": [], "count": 0, "skip": 0, "take": 100}""",
                        status = HttpStatusCode.OK,
                    )
                }
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
        apiService.setSessionToken("old-access")
        apiService.setRefreshToken("refresh-123")

        val results =
            listOf(
                async(Dispatchers.Default) { apiService.loadFeatures() },
                async(Dispatchers.Default) { apiService.loadFeatures() },
            ).map { it.await() }

        assertTrue(results.all { it.isSuccess })
        // The backend consumes a refresh token the moment it is rotated. A
        // concurrent 401 storm must never present the original token a second
        // time — doing so would be rejected and kill the session.
        assertEquals(1, presented.count { it == "refresh-123" })
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
