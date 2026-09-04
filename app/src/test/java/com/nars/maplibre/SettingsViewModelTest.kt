package com.nars.maplibre

import com.nars.maplibre.ui.theme.ThemeMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val appPreferences: AppPreferences = mockk(relaxed = true)
    private val sessionManager: com.nars.maplibre.data.api.SessionManager = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial theme mode comes from preferences`() {
        every { appPreferences.themeMode } returns ThemeMode.DARK

        val viewModel = SettingsViewModel(appPreferences, sessionManager)

        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)
    }

    @Test
    fun `setThemeMode updates flow and preferences`() {
        every { appPreferences.themeMode = any() } just runs

        val viewModel = SettingsViewModel(appPreferences, sessionManager)
        viewModel.setThemeMode(ThemeMode.LIGHT)

        assertEquals(ThemeMode.LIGHT, viewModel.themeMode.value)
        verify { appPreferences.themeMode = ThemeMode.LIGHT }
    }

    @Test
    fun `logout revokes session then invokes navigation callback`() = runTest {
        coEvery { sessionManager.logout() } returns Result.success(Unit)
        every { sessionManager.logout(any(), any()) } answers {
            firstArg<() -> Unit>()()
        }
        val viewModel = SettingsViewModel(appPreferences, sessionManager)
        var navigated = false

        viewModel.logout { navigated = true }
        testDispatcher.scheduler.advanceUntilIdle()

        verify { sessionManager.logout(any(), any()) }
        assertTrue(navigated)
    }

    @Test
    fun `logout navigates even when server revocation fails`() = runTest {
        // SessionManager.logout never throws — it returns a failed Result after
        // clearing local state. Navigation must still happen.
        coEvery { sessionManager.logout() } returns Result.failure(java.io.IOException("offline"))
        every { sessionManager.logout(any(), any()) } answers {
            firstArg<() -> Unit>()()
        }
        val viewModel = SettingsViewModel(appPreferences, sessionManager)
        var navigated = false

        viewModel.logout { navigated = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(navigated)
    }
}
