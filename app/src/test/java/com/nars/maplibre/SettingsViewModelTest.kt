package com.nars.maplibre

import com.nars.maplibre.ui.theme.ThemeMode
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsViewModelTest {
    private val appPreferences: AppPreferences = mockk(relaxed = true)

    @Test
    fun `initial theme mode comes from preferences`() {
        every { appPreferences.themeMode } returns ThemeMode.DARK

        val viewModel = SettingsViewModel(appPreferences)

        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)
    }

    @Test
    fun `setThemeMode updates flow and preferences`() {
        every { appPreferences.themeMode = any() } just runs

        val viewModel = SettingsViewModel(appPreferences)
        viewModel.setThemeMode(ThemeMode.LIGHT)

        assertEquals(ThemeMode.LIGHT, viewModel.themeMode.value)
        verify { appPreferences.themeMode = ThemeMode.LIGHT }
    }
}
