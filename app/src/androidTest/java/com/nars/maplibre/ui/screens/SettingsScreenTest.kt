package com.nars.maplibre.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.nars.maplibre.AppPreferences
import com.nars.maplibre.SettingsViewModel
import com.nars.maplibre.data.api.SessionManager
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class SettingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val mockSessionManager = mockk<SessionManager>(relaxed = true)
    private lateinit var targetContext: android.content.Context

    @Before
    fun setup() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        stopKoin()
        startKoin {
            modules(
                module {
                    single<SessionManager> { mockSessionManager }
                    viewModel { SettingsViewModel(mockk<AppPreferences>(relaxed = true)) }
                },
            )
        }
    }

    @After
    fun teardown() {
        stopKoin()
    }

    @Test
    fun logoutConfirmationCallsSessionManagerAndCallback() {
        var loggedOut = false
        composeTestRule.setContent {
            SettingsScreen(
                onNavigateBack = {},
                onLogout = { loggedOut = true },
            )
        }

        composeTestRule
            .onNodeWithText(targetContext.getString(com.nars.maplibre.R.string.settings_logout))
            .assertIsDisplayed()
            .performClick()

        composeTestRule.waitForIdle()

        composeTestRule
            .onAllNodesWithText(targetContext.getString(com.nars.maplibre.R.string.settings_logout))
            .get(2)
            .performClick()

        composeTestRule.waitForIdle()

        coVerify { mockSessionManager.logout() }
        assertTrue("onLogout should be called after confirm", loggedOut)
    }
}
