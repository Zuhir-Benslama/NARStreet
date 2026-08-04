package com.nars.maplibre.data.api

import com.nars.maplibre.AppPreferences
import com.nars.maplibre.data.model.LoginResponse
import com.nars.maplibre.data.model.User
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionManagerTest {
    private val apiService: ApiService = mockk()
    private val appPreferences: AppPreferences = mockk()
    private lateinit var sessionManager: SessionManager

    @Before
    fun setUp() {
        sessionManager = SessionManager(apiService, appPreferences)
    }

    @Test
    fun `login saves user on success`() = runTest {
        val user = User(username = "testuser", name = "Test User")
        val loginResponse = LoginResponse(user = user, token = "token123")

        coEvery { apiService.login("testuser", "pass123") } returns Result.success(loginResponse)
        every { appPreferences.user = any() } just runs
        every { appPreferences.municipalityName = any() } just runs

        val result = sessionManager.login("testuser", "pass123")

        assertTrue(result.isSuccess)
        verify { appPreferences.user = user.copy(username = "testuser", name = "Test User") }
        verify { appPreferences.municipalityName = loginResponse.municipalityName }
    }

    @Test
    fun `login does not save user on failure`() = runTest {
        coEvery { apiService.login(any(), any()) } returns Result.failure(Exception("Auth failed"))

        val result = sessionManager.login("x", "y")

        assertTrue(result.isFailure)
        verify(exactly = 0) { appPreferences.user = any() }
    }

    @Test
    fun `logout clears all preferences`() = runTest {
        coEvery { apiService.logout() } returns Result.success(Unit)
        every { appPreferences.authToken = null } just runs
        every { appPreferences.refreshToken = null } just runs
        every { appPreferences.user = null } just runs
        every { appPreferences.municipalityName = null } just runs
        every { apiService.setSessionToken(null) } just runs
        every { apiService.setRefreshToken(null) } just runs

        sessionManager.logout()

        verify { appPreferences.authToken = null }
        verify { appPreferences.refreshToken = null }
        verify { appPreferences.user = null }
        verify { appPreferences.municipalityName = null }
        verify { apiService.setSessionToken(null) }
        verify { apiService.setRefreshToken(null) }
    }

    @Test
    fun `isLoggedIn delegates to preferences`() {
        every { appPreferences.isLoggedIn } returns true
        assertTrue(sessionManager.isLoggedIn())

        every { appPreferences.isLoggedIn } returns false
        assertFalse(sessionManager.isLoggedIn())
    }

    @Test
    fun `getUser returns user from preferences`() {
        val user = User(username = "testuser", name = "Test User")
        every { appPreferences.user } returns user
        assertEquals(user, sessionManager.getUser())
    }
}
