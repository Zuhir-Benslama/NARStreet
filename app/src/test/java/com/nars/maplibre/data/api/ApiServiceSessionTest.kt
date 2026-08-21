package com.nars.maplibre.data.api

import com.nars.maplibre.AppPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApiServiceSessionTest {
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
        every { appPreferences.refreshToken } returns null
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
    fun `setSessionToken and getSessionToken round trip`() {
        apiService.setSessionToken("token123")
        assertEquals("token123", apiService.getSessionToken())
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
    fun `refresh failure clears tokens to break stale session loop`() = runTest {
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
        apiService.setSessionToken("stale-access")
        apiService.setRefreshToken("revoked-refresh")

        val result = apiService.loadFeatures()

        assertTrue(result.isFailure)
        assertEquals(null, apiService.getSessionToken())
        assertEquals(null, apiService.getRefreshToken())
        verify { appPreferences.authToken = null }
        verify { appPreferences.refreshToken = null }
    }

    @Test
    fun `transient refresh failure keeps the session for a later retry`() = runTest {
        engine =
            MockEngine { request ->
                when (request.url.encodedPath) {
                    "/api/refresh" ->
                        respond(content = "", status = HttpStatusCode.InternalServerError)

                    else ->
                        respond(content = "", status = HttpStatusCode.Unauthorized)
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
        apiService.setSessionToken("stale-access")
        apiService.setRefreshToken("valid-refresh")

        val result = apiService.loadFeatures()

        // A transient 5xx on refresh must NOT destroy a valid session — the user
        // should be able to retry the action instead of being logged out.
        assertTrue(result.isFailure)
        assertEquals("stale-access", apiService.getSessionToken())
        assertEquals("valid-refresh", apiService.getRefreshToken())
        verify(exactly = 0) { appPreferences.authToken = null }
        verify(exactly = 0) { appPreferences.refreshToken = null }
    }

    @Test
    fun `rejected refresh emits sessionExpired`() = runTest {
        engine =
            MockEngine { _ ->
                respond(content = "", status = HttpStatusCode.Unauthorized)
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
        apiService.setSessionToken("stale-access")
        apiService.setRefreshToken("revoked-refresh")
        var expired = false
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            apiService.sessionExpired.collect { expired = true }
        }

        val result = apiService.loadFeatures()

        assertTrue(result.isFailure)
        assertTrue("sessionExpired must be emitted when the refresh token is rejected", expired)
    }

    @Test
    fun `refresh that reissues the same access token still retries the request`() = runTest {
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
                                "access_token=old-access; refresh_token=new-refresh",
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
        apiService.setSessionToken("old-access")
        apiService.setRefreshToken("refresh-123")

        val result = apiService.loadFeatures()

        // Even when the backend re-issues the identical access token, a fresh
        // refresh succeeded — the original request must be retried.
        assertTrue(result.isSuccess)
        assertEquals(3, callCount)
        assertEquals("new-refresh", apiService.getRefreshToken())
    }

    @Test
    fun `failed login does not adopt session cookies`() = runTest {
        engine =
            MockEngine { _ ->
                respond(
                    content = """{"success": false, "message": "Invalid credentials"}""",
                    status = HttpStatusCode.OK,
                    headers =
                    io.ktor.http.headersOf(
                        "Set-Cookie",
                        "access_token=stale; refresh_token=stale-refresh",
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
        apiService = ApiService(client, appPreferences)

        val result = apiService.login("testuser", "wrong")

        assertTrue(result.isFailure)
        assertEquals(null, apiService.getSessionToken())
        assertEquals(null, apiService.getRefreshToken())
        verify(exactly = 0) { appPreferences.authToken = any() }
    }

    @Test
    fun `failed login with unparseable body leaves no tokens`() = runTest {
        engine =
            MockEngine { _ ->
                respond(
                    content = "not-json-at-all",
                    status = HttpStatusCode.OK,
                    headers =
                    io.ktor.http.headersOf(
                        "Set-Cookie",
                        "access_token=stale; refresh_token=stale-refresh",
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
        apiService = ApiService(client, appPreferences)

        val result = apiService.login("testuser", "wrong")

        assertTrue(result.isFailure)
        assertEquals(null, apiService.getSessionToken())
        assertEquals(null, apiService.getRefreshToken())
    }

    @Test
    fun `refresh falls back to the persisted token when memory is cleared`() = runTest {
        var callCount = 0
        engine =
            MockEngine { request ->
                callCount++
                when {
                    request.url.encodedPath == "/api/refresh" ->
                        respond(
                            content = """{"success": true}""",
                            status = HttpStatusCode.OK,
                            headers = io.ktor.http.headersOf(
                                "Set-Cookie",
                                "access_token=new-access; refresh_token=new-refresh",
                            ),
                        )

                    callCount == 1 ->
                        respond(content = "", status = HttpStatusCode.Unauthorized)

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
        apiService.setSessionToken("stale-access")
        every { appPreferences.refreshToken } returns "persisted-refresh"

        val result = apiService.loadFeatures()

        // Backgrounding clears the in-memory tokens; the refresh must still be
        // able to rotate using the persisted refresh token.
        assertTrue(result.isSuccess)
        assertEquals("new-access", apiService.getSessionToken())
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

                    else -> {
                        respond(
                            content = """{"features": [], "count": 0, "skip": 0, "take": 100}""",
                            status = HttpStatusCode.OK,
                        )
                    }
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
}
